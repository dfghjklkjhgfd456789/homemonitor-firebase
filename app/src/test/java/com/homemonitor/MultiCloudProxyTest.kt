package com.homemonitor

import com.google.android.system.Placeholder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MultiCloudProxyTest
 *
 * Unit tests for the 35-channel multi-provider proxy client and matrix configuration.
 */
class MultiCloudProxyTest {

    @Test
    fun test35ChannelsMatrixConfiguration() {
        val allEndpoints = MultiCloudProxyClient.getAllEndpoints()
        assertNotNull("Endpoints list should not be null", allEndpoints)
        assertEquals("Should have exactly 35 failover channels configured", 35, allEndpoints.size)

        assertEquals("Should have 10 Cloudflare Worker channels", 10, Placeholder.CLOUDFLARE_WORKERS.size)
        assertEquals("Should have 5 Deno Deploy channels", 5, Placeholder.DENO_WORKERS.size)
        assertEquals("Should have 5 Supabase Edge channels", 5, Placeholder.SUPABASE_WORKERS.size)
        assertEquals("Should have 5 Vercel channels", 5, Placeholder.VERCEL_WORKERS.size)
        assertEquals("Should have 5 Glitch channels", 5, Placeholder.GLITCH_WORKERS.size)
        assertEquals("Should have 5 Koyeb channels", 5, Placeholder.KOYEB_WORKERS.size)
    }

    @Test
    fun testEndpointProtocolValidation() {
        val allEndpoints = MultiCloudProxyClient.getAllEndpoints()
        for (url in allEndpoints) {
            assertTrue("Endpoint URL '$url' must start with https://", url.startsWith("https://"))
        }
    }

    @Test
    fun testProvisioningAuthSecretNotNull() {
        val secret = Placeholder.CLOUDFLARE_PROVISIONING_AUTH_SECRET
        assertNotNull("Shared auth secret must not be null", secret)
        assertTrue("Shared auth secret must not be blank", secret.isNotBlank())
    }
}
