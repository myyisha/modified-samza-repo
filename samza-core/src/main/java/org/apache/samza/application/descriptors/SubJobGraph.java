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

package org.apache.samza.application.descriptors;

import org.apache.samza.application.StreamApplication;
import org.apache.samza.config.Config;
import org.apache.samza.operators.spec.InputOperatorSpec;
import org.apache.samza.operators.spec.OutputStreamImpl;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class SubJobGraph {
    private Map<String, InputOperatorSpec> inputOperators = new LinkedHashMap<>();
    private Map<String, OutputStreamImpl> outputStreams = new LinkedHashMap<>();
    private Set<String> operatorIds = new HashSet<>();

    public SubJobGraph(Map<String, InputOperatorSpec> inputOperators, Map<String, OutputStreamImpl> outputStreams, Set<String> operatorIds) {
        this.inputOperators = inputOperators;
        this.outputStreams = outputStreams;
        this.operatorIds = operatorIds;
    }

    public SubJobGraph() {
        this(new LinkedHashMap<>(), new LinkedHashMap<>(), new HashSet<>());
    }

    public void addInputOperators(Map<String, InputOperatorSpec> inputOperators) {
        for (Map.Entry<String,InputOperatorSpec> entry : inputOperators.entrySet()) {
            this.inputOperators.put(entry.getKey(), entry.getValue());
        }
    }

    public Map<String, InputOperatorSpec> getInputOperators() {
        return this.inputOperators;
    }

    public void addOutputStreams(Map<String, OutputStreamImpl> outputStreams) {
        for (Map.Entry<String,OutputStreamImpl> entry : outputStreams.entrySet()) {
            this.outputStreams.put(entry.getKey(), entry.getValue());
        }
    }

    public Map<String, OutputStreamImpl> getOutputStreams() {
        return this.outputStreams;
    }

    public void addOperatorIds(Set<String> operatorIds) {
        this.operatorIds.addAll(operatorIds);
    }

    public Set<String> getOperatorIds() {
        return this.operatorIds;
    }
}
