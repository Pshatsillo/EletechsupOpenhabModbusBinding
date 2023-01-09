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

import static org.openhab.binding.eletechsup.internal.EletechsupBindingConstants.*;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.modbus.handler.ModbusEndpointThingHandler;
import org.openhab.core.io.transport.modbus.AsyncModbusFailure;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusCommunicationInterface;
import org.openhab.core.io.transport.modbus.ModbusConstants;
import org.openhab.core.io.transport.modbus.ModbusReadFunctionCode;
import org.openhab.core.io.transport.modbus.ModbusReadRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.io.transport.modbus.PollTask;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EletechsupHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class EletechsupHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(EletechsupHandler.class);
    private @Nullable EletechsupConfiguration config;
    protected volatile @Nullable ModbusCommunicationInterface comms = null;
    protected ThingTypeUID type;
    private ArrayList<PollTask> pollTasks = new ArrayList<PollTask>();
    private Integer[] registers = new Integer[0];

    public EletechsupHandler(Thing thing) {
        super(thing);
        this.type = thing.getThingTypeUID();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        config = getConfigAs(EletechsupConfiguration.class);
        logger.debug("Initializing thing with configuration: {}", thing.getConfiguration());
        startUp();
    }

    private void startUp() {
        connectEndpoint();
        if (comms == null || config == null) {
            logger.debug("Invalid endpoint/config/manager ref for studer handler");
            return;
        }

        if (!pollTasks.isEmpty()) {
            return;
        }

        if (type.equals(THING_TYPE_ELETECHSUP)) {
            Set<Integer> keys = CHANNELS_ELETECHSUP.keySet();
            registers = keys.toArray(new Integer[keys.size()]);
        }

        for (int r : registers) {
            registerPollTask(r);
        }
    }

    private synchronized void registerPollTask(int registerNumber) {
        if (pollTasks.size() >= registers.length) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
            throw new IllegalStateException("New pollTask invalid");
        }
        ModbusCommunicationInterface mycomms = comms;
        EletechsupConfiguration shitConfig = config;
        if (shitConfig == null || mycomms == null) {
            throw new IllegalStateException("registerPollTask called without proper configuration");
        }

        logger.debug("Setting up regular polling");

        ModbusReadRequestBlueprint request = new ModbusReadRequestBlueprint(shitConfig.slaveAddress,
                ModbusReadFunctionCode.READ_INPUT_REGISTERS, registerNumber, 2, shitConfig.maxTries);
        long refreshMillis = shitConfig.refresh * 1000L;
        PollTask pollTask = mycomms.registerRegularPoll(request, refreshMillis, 1000, result -> {
            if (result.getRegisters().isPresent()) {
                ModbusRegisterArray reg = result.getRegisters().get();
                handlePolledData(registerNumber, reg);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                return;
            }
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
        }, this::handleError);
        pollTasks.add(pollTask);
    }

    private @Nullable ModbusEndpointThingHandler getEndpointThingHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            logger.debug("Bridge is null");
            return null;
        }
        if (bridge.getStatus() != ThingStatus.ONLINE) {
            logger.debug("Bridge is not online");
            return null;
        }

        ThingHandler handler = bridge.getHandler();
        if (handler == null) {
            logger.debug("Bridge handler is null");
            return null;
        }

        if (handler instanceof ModbusEndpointThingHandler) {
            ModbusEndpointThingHandler slaveEndpoint = (ModbusEndpointThingHandler) handler;
            return slaveEndpoint;
        } else {
            logger.debug("Unexpected bridge handler: {}", handler);
            return null;
        }
    }

    private void connectEndpoint() {
        if (comms != null) {
            return;
        }

        ModbusEndpointThingHandler slaveEndpointThingHandler = getEndpointThingHandler();
        if (slaveEndpointThingHandler == null) {
            @SuppressWarnings("null")
            String label = Optional.ofNullable(getBridge()).map(b -> b.getLabel()).orElse("<null>");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    String.format("Bridge '%s' is offline", label));
            logger.debug("No bridge handler available -- aborting init for {}", label);
            return;
        }
        comms = slaveEndpointThingHandler.getCommunicationInterface();
        if (comms == null) {
            @SuppressWarnings("null")
            String label = Optional.ofNullable(getBridge()).map(b -> b.getLabel()).orElse("<null>");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    String.format("Bridge '%s' not completely initialized", label));
            logger.debug("Bridge not initialized fully (no endpoint) -- aborting init for {}", this);
            return;
        }
    }

    protected void handlePolledData(int registerNumber, ModbusRegisterArray registers) {
        Optional<DecimalType> quantity = ModbusBitUtilities.extractStateFromRegisters(registers, 0,
                ModbusConstants.ValueType.FLOAT32);
        quantity.ifPresent(value -> {
            if (type.equals(THING_TYPE_ELETECHSUP)) {
                // handlePolledDataXtender(registerNumber, value);
            }
        });
        resetCommunicationError();
    }

    private synchronized void unregisterPollTasks() {
        if (pollTasks.isEmpty()) {
            return;
        }
        logger.debug("Unregistering polling from ModbusManager");
        ModbusCommunicationInterface mycomms = comms;
        if (mycomms != null) {
            for (PollTask t : pollTasks) {
                mycomms.unregisterRegularPoll(t);
            }
            pollTasks.clear();
        }
    }

    /**
     * Remove the endpoint if exists
     */
    private void unregisterEndpoint() {
        // Comms will be close()'d by endpoint thing handler
        comms = null;
    }

    /**
     * Handle errors received during communication
     */
    protected void handleError(AsyncModbusFailure<ModbusReadRequestBlueprint> failure) {
        // Ignore all incoming data and errors if configuration is not correct
        if (hasConfigurationError() || getThing().getStatus() == ThingStatus.OFFLINE) {
            return;
        }
        String msg = failure.getCause().getMessage();
        String cls = failure.getCause().getClass().getName();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                String.format("Error with read: %s: %s", cls, msg));
    }

    /**
     * Returns true, if we're in a CONFIGURATION_ERROR state
     *
     * @return
     */
    protected boolean hasConfigurationError() {
        ThingStatusInfo statusInfo = getThing().getStatusInfo();
        return statusInfo.getStatus() == ThingStatus.OFFLINE
                && statusInfo.getStatusDetail() == ThingStatusDetail.CONFIGURATION_ERROR;
    }

    /**
     * Reset communication status to ONLINE if we're in an OFFLINE state
     */
    protected void resetCommunicationError() {
        ThingStatusInfo statusInfo = thing.getStatusInfo();
        if (ThingStatus.OFFLINE.equals(statusInfo.getStatus())
                && ThingStatusDetail.COMMUNICATION_ERROR.equals(statusInfo.getStatusDetail())) {
            updateStatus(ThingStatus.ONLINE);
        }
    }

    /**
     * Dispose the binding correctly
     */
    @Override
    public void dispose() {
        tearDown();
    }

    /**
     * Unregister the poll tasks and release the endpoint reference
     */
    private void tearDown() {
        unregisterPollTasks();
        unregisterEndpoint();
    }
}
