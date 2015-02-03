/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.examples.compute.failover;

import org.apache.ignite.*;
import org.apache.ignite.compute.*;
import org.apache.ignite.examples.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.resources.*;
import org.apache.ignite.internal.util.lang.*;

import java.util.*;

/**
 * Demonstrates the usage of checkpoints in GridGain.
 * <p>
 * The example tries to compute phrase length. In order to mitigate possible node failures, intermediate
 * result is saved as as checkpoint after each job step.
 * <p>
 * Remote nodes must be started using {@link ComputeFailoverNodeStartup}.
 */
public class ComputeFailoverExample {
    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     * @throws IgniteCheckedException If example execution failed.
     */
    public static void main(String[] args) throws IgniteCheckedException {
        try (Ignite g = Ignition.start(ComputeFailoverNodeStartup.configuration())) {
            if (!ExamplesUtils.checkMinTopologySize(g.cluster(), 2))
                return;

            System.out.println();
            System.out.println("Compute failover example started.");

            // Number of letters.
            int charCnt = g.compute().apply(new CheckPointJob(), "Stage1 Stage2");

            System.out.println();
            System.out.println(">>> Finished executing fail-over example with checkpoints.");
            System.out.println(">>> Total number of characters in the phrase is '" + charCnt + "'.");
            System.out.println(">>> You should see exception stack trace from failed job on some node.");
            System.out.println(">>> Failed job will be failed over to another node.");
        }
    }

    @ComputeTaskSessionFullSupport
    private static final class CheckPointJob implements IgniteClosure<String, Integer> {
        /** Injected distributed task session. */
        @IgniteTaskSessionResource
        private ComputeTaskSession jobSes;

        /** Injected grid logger. */
        @IgniteLoggerResource
        private IgniteLogger log;

        /** */
        private IgniteBiTuple<Integer, Integer> state;

        /** */
        private String phrase;

        /**
         * The job will check the checkpoint with key '{@code fail}' and if
         * it's {@code true} it will throw exception to simulate a failure.
         * Otherwise, it will execute the grid-enabled method.
         */
        @Override public Integer apply(String phrase) {
            System.out.println();
            System.out.println(">>> Executing fail-over example job.");

            this.phrase = phrase;

            List<String> words = Arrays.asList(phrase.split(" "));

            final String cpKey = checkpointKey();

            IgniteBiTuple<Integer, Integer> state = jobSes.loadCheckpoint(cpKey);

            int idx = 0;
            int sum = 0;

            if (state != null) {
                this.state = state;

                // Last processed word index and total length.
                idx = state.get1();
                sum = state.get2();
            }

            for (int i = idx; i < words.size(); i++) {
                sum += words.get(i).length();

                this.state = new IgniteBiTuple<>(i + 1, sum);

                // Save checkpoint with scope of task execution.
                // It will be automatically removed when task completes.
                jobSes.saveCheckpoint(cpKey, this.state);

                // For example purposes, we fail on purpose after first stage.
                // This exception will cause job to be failed over to another node.
                if (i == 0) {
                    System.out.println();
                    System.out.println(">>> Job will be failed over to another node.");

                    throw new ComputeJobFailoverException("Expected example job exception.");
                }
            }

            return sum;
        }

        /**
         * Make reasonably unique checkpoint key.
         *
         * @return Checkpoint key.
         */
        private String checkpointKey() {
            return getClass().getName() + '-' + phrase;
        }
    }
}
