/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.sle.utl.test;

import com.beanit.jasn1.ber.types.BerNull;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafStartReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafTransferDataInvocation;
import eu.dariolucia.ccsds.sle.utl.LightweightStatsRecorder;
import eu.dariolucia.ccsds.sle.utl.OperationRecorder;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.RateSample;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceBindingStateEnum;
import eu.dariolucia.ccsds.sle.utl.si.UnbindReasonEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafRequestedFrameQualityEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstance;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class RafPerformanceTest {

    @Test
    void testPerformance() throws IOException, InterruptedException {
        Logger.getLogger("").setLevel(Level.OFF);
        Arrays.stream(Logger.getLogger("").getHandlers()).forEach(o -> o.setLevel(Level.OFF));

        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(10); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(10); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        // Register listener
        LightweightStatsRecorder recorder = new LightweightStatsRecorder();
        rafUser.register(recorder);

        rafUser.bind(2);

        // Check reported state
        assertTrue(rafUser.waitForState(ServiceInstanceBindingStateEnum.READY, 5000));
        AwaitUtil.await(2000);
        assertTrue(recorder.getStates().size() > 0);
        assertEquals(ServiceInstanceBindingStateEnum.READY, recorder.getStates().get(recorder.getStates().size() - 1).getState());

        // Start
        rafUser.start(null, null, RafRequestedFrameQualityEnum.GOOD_FRAMES_ONLY);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived() == 1L);
        assertEquals(1L, recorder.getPduReceived());

        AtomicBoolean generationRunning = new AtomicBoolean(true);
        // Start provider thread
        Thread generationThread = new Thread(() -> {
            // Send transfer data, fast
            byte[] frameToTransfer = new byte[1115];
            while(generationRunning.get()) {
                rafProvider.transferData(frameToTransfer, 0, 0, Instant.now(), false, "AABBCCDD", false, new byte[10]);
            }
            // Send end of data
            rafProvider.endOfData();
        });
        generationThread.start();

        int secondsToWait = 20;
        // Get data rate
        RateSample rs1 = rafUser.getCurrentRate();
        System.out.println(rs1);
        AwaitUtil.await(secondsToWait * 1000);

        // Get data rate
        RateSample rs2 = rafUser.getCurrentRate();
        System.out.println(rs2);
        assertNotNull(rs2.getInstant());
        assertNotNull(rs2.getByteSample().getDate());

        System.out.println("PDU  RX in " + secondsToWait + " seconds: " + (rs2.getPduSample().getTotalInUnits() - rs1.getPduSample().getTotalInUnits()));
        System.out.println("PDU  RX data rate: " + rs2.getPduSample().getInRate());
        System.out.println("(TML) Byte RX data rate: " + rs2.getByteSample().getInRate() + " bytes/sec");
        System.out.println("(TML) Bits RX data rate: " + (rs2.getByteSample().getInRate() / (1024 * 1024)) * 8 + " Mbps");
        generationRunning.set(false);

        // Check how many transfer datas the recorder received and compute the amount of data received
        long numTransferredBytes = recorder.getTransferDataBytesReceived();
        System.out.println("(SI) PDU received: " + recorder.getPduReceived());
        System.out.println("(SI) Bits frame RX data rate: " + (((numTransferredBytes * 8) / secondsToWait) / (1024 * 1024)) + " Mbps");

        // Stop
        rafUser.stop();
        AwaitUtil.awaitCondition(5000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.READY);

        // Unbind
        rafUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(2000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }
}
