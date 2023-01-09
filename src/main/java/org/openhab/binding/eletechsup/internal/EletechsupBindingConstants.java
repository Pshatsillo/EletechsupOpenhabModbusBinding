/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.eletechsup.internal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.modbus.ModbusBindingConstants;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link EletechsupBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class EletechsupBindingConstants {

    private static final String BINDING_ID = ModbusBindingConstants.BINDING_ID;

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ELETECHSUP = new ThingTypeUID(BINDING_ID, "sample");

    // List of all Channel ids
    public static final String CHANNEL_1 = "channel1";

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = new HashSet<>();
    static {
        SUPPORTED_THING_TYPES_UIDS.add(THING_TYPE_ELETECHSUP);
    }
    public static final Map<Integer, String> CHANNELS_ELETECHSUP = new HashMap<>();
    static {
        CHANNELS_ELETECHSUP.put(6, CHANNEL_1);
        // CHANNELS_CHINASHIT.put(0, CHANNEL_BATTERY_VOLTAGE);
        // CHANNELS_CHINASHIT.put(2, CHANNEL_BATTERY_CURRENT);
        // CHANNELS_CHINASHIT.put(4, CHANNEL_STATE_OF_CHARGE);
        // CHANNELS_CHINASHIT.put(58, CHANNEL_BATTERY_TEMPERATURE);
    }
}
