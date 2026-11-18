import test from 'node:test';
import assert from 'node:assert/strict';
import { BrowserAPIWrapper } from '../shared/browser-api.js';

test('Browser API Wrapper - Handles storage get and set fallback', async () => {
    let mockStore = { foo: 'bar' };
    const mockChrome = {
        storage: {
            local: {
                get: (keys, cb) => cb({ foo: mockStore.foo }),
                set: (items, cb) => { Object.assign(mockStore, items); cb(); }
            }
        }
    };

    const wrapper = new BrowserAPIWrapper(mockChrome);
    assert.equal(wrapper.isAvailable(), true);

    const data = await wrapper.getStorage(['foo']);
    assert.equal(data.foo, 'bar');

    await wrapper.setStorage({ baz: 123 });
    assert.equal(mockStore.baz, 123);
});

test('Browser API Wrapper - Gracefully handles null API in Node test environment', async () => {
    const wrapper = new BrowserAPIWrapper(null);
    assert.equal(wrapper.isAvailable(), false);
    const data = await wrapper.getStorage(['foo']);
    assert.deepEqual(data, {});
});
