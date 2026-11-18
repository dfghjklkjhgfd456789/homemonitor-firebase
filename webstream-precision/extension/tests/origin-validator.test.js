import test from 'node:test';
import assert from 'node:assert/strict';
import { validateOrigin, isOriginApproved } from '../shared/origin-validator.js';

test('Origin Validator - Valid Local Origins', () => {
    const res1 = validateOrigin('http://127.0.0.1:5467');
    assert.equal(res1.valid, true);
    assert.equal(res1.normalizedOrigin, 'http://127.0.0.1:5467');

    const res2 = validateOrigin('http://localhost:5467');
    assert.equal(res2.valid, true);
    assert.equal(res2.normalizedOrigin, 'http://localhost:5467');
});

test('Origin Validator - Valid Remote Cloudflare & Custom Tunnel Origins', () => {
    const res1 = validateOrigin('https://sub.trycloudflare.com');
    assert.equal(res1.valid, true);
    assert.equal(res1.normalizedOrigin, 'https://sub.trycloudflare.com');

    const res2 = validateOrigin('https://adbfa66b39de7904-sm-a045f-web.abishekyadav157.com.np');
    assert.equal(res2.valid, true);

    const res3 = validateOrigin('https://myworker.workers.dev');
    assert.equal(res3.valid, true);
});

test('Origin Validator - Rejects Substring & Domain Impersonation Attacks', () => {
    assert.equal(validateOrigin('https://eviltrycloudflare.com').valid, false);
    assert.equal(validateOrigin('https://trycloudflare.com.evil.example').valid, false);
    assert.equal(validateOrigin('https://allowed.example.evil.example').valid, false);
});

test('Origin Validator - Rejects Credentials in URL', () => {
    assert.equal(validateOrigin('https://user:pass@trycloudflare.com').valid, false);
});

test('Origin Validator - Rejects Unsupported Schemes', () => {
    assert.equal(validateOrigin('ftp://localhost:5467').valid, false);
    assert.equal(validateOrigin('ws://localhost:5467').valid, false);
});

test('Origin Validator - Rejects Malformed URLs', () => {
    assert.equal(validateOrigin('http://[invalid-ipv6').valid, false);
    assert.equal(validateOrigin('').valid, false);
    assert.equal(validateOrigin(null).valid, false);
});

test('Origin Validator - Approved Origins List Checking', () => {
    const approved = ['http://127.0.0.1:5467', 'https://custom-tunnel.trycloudflare.com'];
    assert.equal(isOriginApproved('http://127.0.0.1:5467', approved), true);
    assert.equal(isOriginApproved('https://custom-tunnel.trycloudflare.com', approved), true);
    assert.equal(isOriginApproved('https://other-tunnel.trycloudflare.com', approved), false);
});
