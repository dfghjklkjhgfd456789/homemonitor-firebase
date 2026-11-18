import fs from 'fs';
import path from 'path';

const iconDir = path.join(process.cwd(), 'extension/assets/icons');
if (!fs.existsSync(iconDir)) {
    fs.mkdirSync(iconDir, { recursive: true });
}

// 1x1 valid PNG base64
const pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR1gAAAAABJRU5ErkJggg==';
const pngBuffer = Buffer.from(pngBase64, 'base64');

['icon16.png', 'icon48.png', 'icon128.png'].forEach(filename => {
    fs.writeFileSync(path.join(iconDir, filename), pngBuffer);
});

console.log('Icons generated successfully.');
