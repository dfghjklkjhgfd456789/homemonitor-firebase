import fs from 'fs';
import path from 'path';
import zlib from 'zlib';

const rootDir = process.cwd();
const distDir = path.join(rootDir, 'dist');
const chromiumDist = path.join(distDir, 'chromium');
const firefoxDist = path.join(distDir, 'firefox');

// Clean dist directory
if (fs.existsSync(distDir)) {
    fs.rmSync(distDir, { recursive: true, force: true });
}

fs.mkdirSync(chromiumDist, { recursive: true });
fs.mkdirSync(firefoxDist, { recursive: true });

function copyRecursive(src, dest) {
    const stats = fs.statSync(src);
    if (stats.isDirectory()) {
        if (!fs.existsSync(dest)) fs.mkdirSync(dest, { recursive: true });
        for (const child of fs.readdirSync(src)) {
            copyRecursive(path.join(src, child), path.join(dest, child));
        }
    } else {
        fs.copyFileSync(src, dest);
    }
}

// Copy extension files
copyRecursive(path.join(rootDir, 'extension'), path.join(chromiumDist, 'extension'));
copyRecursive(path.join(rootDir, 'extension'), path.join(firefoxDist, 'extension'));

// Copy manifests to root of target build
fs.copyFileSync(path.join(rootDir, 'manifests/chromium/manifest.json'), path.join(chromiumDist, 'manifest.json'));
fs.copyFileSync(path.join(rootDir, 'manifests/firefox/manifest.json'), path.join(firefoxDist, 'manifest.json'));

console.log('✅ Unpacked build directories created.');

// CRC32 Calculation
function makeCrcTable() {
    const table = new Uint32Array(256);
    for (let i = 0; i < 256; i++) {
        let c = i;
        for (let k = 0; k < 8; k++) {
            c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
        }
        table[i] = c >>> 0;
    }
    return table;
}
const crcTable = makeCrcTable();

function crc32(buf) {
    let crc = 0xFFFFFFFF;
    for (let i = 0; i < buf.length; i++) {
        crc = (crc >>> 8) ^ crcTable[(crc ^ buf[i]) & 0xFF];
    }
    return (crc ^ 0xFFFFFFFF) >>> 0;
}

// Simple ZIP Builder
function createZip(sourceDir, zipFilePath) {
    const files = [];

    function collectFiles(dir, base) {
        for (const item of fs.readdirSync(dir)) {
            const full = path.join(dir, item);
            const rel = base ? `${base}/${item}` : item;
            if (fs.statSync(full).isDirectory()) {
                collectFiles(full, rel);
            } else {
                files.push({ full, rel });
            }
        }
    }
    collectFiles(sourceDir, '');

    const localHeaders = [];
    const cdHeaders = [];
    let offset = 0;

    for (const file of files) {
        const data = fs.readFileSync(file.full);
        const crc = crc32(data);
        const filenameBuf = Buffer.from(file.rel, 'utf8');

        // Store mode (uncompressed) for simple zip compatibility
        const compData = data;
        const compSize = compData.length;
        const uncompSize = data.length;

        // Local header
        const lh = Buffer.alloc(30 + filenameBuf.length);
        lh.writeUInt32LE(0x04034b50, 0); // signature
        lh.writeUInt16LE(20, 4);        // ver
        lh.writeUInt16LE(0, 6);         // flags
        lh.writeUInt16LE(0, 8);         // compression: 0 store
        lh.writeUInt16LE(0, 10);        // mod time
        lh.writeUInt16LE(0, 12);        // mod date
        lh.writeUInt32LE(crc, 14);      // crc32
        lh.writeUInt32LE(compSize, 18); // comp size
        lh.writeUInt32LE(uncompSize, 22); // uncomp size
        lh.writeUInt16LE(filenameBuf.length, 26); // name len
        lh.writeUInt16LE(0, 28);        // extra len
        filenameBuf.copy(lh, 30);

        localHeaders.push(lh, compData);

        // Central directory header
        const cdh = Buffer.alloc(46 + filenameBuf.length);
        cdh.writeUInt32LE(0x02014b50, 0); // signature
        cdh.writeUInt16LE(20, 4);         // ver made by
        cdh.writeUInt16LE(20, 6);         // ver needed
        cdh.writeUInt16LE(0, 8);          // flags
        cdh.writeUInt16LE(0, 10);         // compression: 0 store
        cdh.writeUInt16LE(0, 12);         // mod time
        cdh.writeUInt16LE(0, 14);         // mod date
        cdh.writeUInt32LE(crc, 16);       // crc32
        cdh.writeUInt32LE(compSize, 20);  // comp size
        cdh.writeUInt32LE(uncompSize, 24); // uncomp size
        cdh.writeUInt16LE(filenameBuf.length, 28); // name len
        cdh.writeUInt16LE(0, 30);         // extra len
        cdh.writeUInt16LE(0, 32);         // comment len
        cdh.writeUInt16LE(0, 34);         // disk start
        cdh.writeUInt16LE(0, 36);         // int attr
        cdh.writeUInt32LE(0, 38);         // ext attr
        cdh.writeUInt32LE(offset, 42);    // local header offset
        filenameBuf.copy(cdh, 46);

        cdHeaders.push(cdh);
        offset += lh.length + compData.length;
    }

    const cdOffset = offset;
    let cdSize = 0;
    for (const cdh of cdHeaders) {
        cdSize += cdh.length;
    }

    // End of central directory record
    const eocd = Buffer.alloc(22);
    eocd.writeUInt32LE(0x06054b50, 0); // signature
    eocd.writeUInt16LE(0, 4);          // disk num
    eocd.writeUInt16LE(0, 6);          // start disk
    eocd.writeUInt16LE(files.length, 8); // records on disk
    eocd.writeUInt16LE(files.length, 10); // total records
    eocd.writeUInt32LE(cdSize, 12);     // cd size
    eocd.writeUInt32LE(cdOffset, 16);   // cd offset
    eocd.writeUInt16LE(0, 20);          // comment len

    const zipBuffer = Buffer.concat([...localHeaders, ...cdHeaders, eocd]);
    fs.writeFileSync(zipFilePath, zipBuffer);
    console.log(`📦 Created ${path.basename(zipFilePath)} (${zipBuffer.length} bytes)`);
}

createZip(chromiumDist, path.join(distDir, 'webstream-precision-chromium.zip'));
createZip(firefoxDist, path.join(distDir, 'webstream-precision-firefox.zip'));

console.log('🎉 Extension build and packaging completed successfully!');
