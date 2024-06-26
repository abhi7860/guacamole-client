/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.guacamole.auth.sso;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.guacamole.net.auth.IdentifierGenerator;

/**
 * Service for generating and validating single-use random tokens (nonces).
 * Each generated nonce is at least 128 bits and case-insensitive.
 */
public class NonceService {

    /**
     * Map of all generated nonces to their corresponding expiration timestamps.
     * This Map must be periodically swept of expired nonces to avoid growing
     * without bound.
     */
    private final Map<String, Long> nonces = new ConcurrentHashMap<>();

    /**
     * The timestamp of the last expired nonce sweep.
     */
    private long lastSweep = System.currentTimeMillis();

    /**
     * The minimum number of bits of entropy to include in each nonce.
     */
    private static final int NONCE_BITS = 128;

    /**
     * The minimum amount of time to wait between sweeping expired nonces from
     * the Map.
     */
    private static final long SWEEP_INTERVAL = 60000;

    /**
     * Iterates through the entire Map of generated nonces, removing any nonce
     * that has exceeded its expiration timestamp. If insufficient time has
     * elapsed since the last sweep, as dictated by SWEEP_INTERVAL, this
     * function has no effect.
     */
    private void sweepExpiredNonces() {

        // Do not sweep until enough time has elapsed since the last sweep
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSweep < SWEEP_INTERVAL)
            return;

        // Record time of sweep
        lastSweep = currentTime;

        // For each stored nonce
        Iterator<Map.Entry<String, Long>> entries = nonces.entrySet().iterator();
        while (entries.hasNext()) {

            // Remove all entries which have expired
            Map.Entry<String, Long> current = entries.next();
            if (current.getValue() <= System.currentTimeMillis())
                entries.remove();

        }

    }

    /**
     * Generates a cryptographically-secure nonce value. The nonce is intended
     * to be used to prevent replay attacks.
     *
     * @param maxAge
     *     The maximum amount of time that the generated nonce should remain
     *     valid, in milliseconds.
     *
     * @return
     *     A cryptographically-secure nonce value. Generated nonces are at
     *     least 128-bit and are case-insensitive.
     */
    public String generate(long maxAge) {

        // Sweep expired nonces if enough time has passed
        sweepExpiredNonces();

        // Generate and store nonce, along with expiration timestamp
        String nonce = IdentifierGenerator.generateIdentifier(NONCE_BITS, false);
        nonces.put(nonce, System.currentTimeMillis() + maxAge);
        return nonce;

    }

    /**
     * Returns whether the give nonce value is valid. A nonce is valid if and
     * only if it was generated by this instance of the NonceService. Testing
     * nonce validity through this function immediately and permanently
     * invalidates that nonce.
     *
     * @param nonce
     *     The nonce value to test. This value may be null, which will be
     *     considered an invalid nonce. Comparisons are case-insensitive.
     *
     * @return
     *     true if the provided nonce is valid, false otherwise.
     */
    public boolean isValid(String nonce) {

        // All null nonces are invalid.
        if (nonce == null)
            return false;

        // Remove nonce, verifying whether it was present at all
        Long expires = nonces.remove(nonce.toLowerCase(Locale.US));
        if (expires == null)
            return false;

        // Nonce is only valid if it hasn't expired
        return expires > System.currentTimeMillis();

    }

}
