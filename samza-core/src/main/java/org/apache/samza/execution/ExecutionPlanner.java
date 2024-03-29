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

package org.apache.samza.execution;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.ListUtils;
import org.apache.samza.SamzaException;
import org.apache.samza.application.StreamApplication;
import org.apache.samza.application.descriptors.ApplicationDescriptor;
import org.apache.samza.application.descriptors.ApplicationDescriptorImpl;
import org.apache.samza.application.LegacyTaskApplication;
import org.apache.samza.application.descriptors.ApplicationDescriptorUtil;
import org.apache.samza.config.ApplicationConfig;
import org.apache.samza.config.ClusterManagerConfig;
import org.apache.samza.config.Config;
import org.apache.samza.config.JobConfig;
import org.apache.samza.config.StreamConfig;
import org.apache.samza.serializers.SerializableSerde;
import org.apache.samza.table.descriptors.BaseTableDescriptor;
import org.apache.samza.operators.spec.InputOperatorSpec;
import org.apache.samza.operators.spec.OperatorSpec;
import org.apache.samza.operators.spec.StreamTableJoinOperatorSpec;
import org.apache.samza.system.StreamSpec;
import org.apache.samza.table.TableSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.samza.util.StreamUtil.getStreamSpec;
import static org.apache.samza.util.StreamUtil.getStreamSpecs;


/**
 * The ExecutionPlanner creates the physical execution graph for the {@link ApplicationDescriptorImpl}, and
 * the intermediate topics needed for the execution.
 */
// TODO: ExecutionPlanner needs to be able to generate single node JobGraph for low-level TaskApplication as well (SAMZA-1811)
public class ExecutionPlanner {
  private static final Logger log = LoggerFactory.getLogger(ExecutionPlanner.class);
  private final static SerializableSerde<ApplicationDescriptorImpl<? extends ApplicationDescriptor>> APPDESC_SERDE = new SerializableSerde<>();

  private final Config config;
  private final StreamManager streamManager;
  private final StreamConfig streamConfig;

  public ExecutionPlanner(Config config, StreamManager streamManager) {
    this.config = config;
    this.streamManager = streamManager;
    this.streamConfig = new StreamConfig(config);
  }

  public ExecutionPlan plan(ApplicationDescriptorImpl<? extends ApplicationDescriptor> appDesc, int i) {
    validateConfig();

    // Create physical job graph based on stream graph
    JobGraph jobGraph = createJobGraph(appDesc, i);

    // Fetch the external streams partition info
    setInputAndOutputStreamPartitionCount(jobGraph);

    // Group streams participating in joins together into sets
    List<StreamSet> joinedStreamSets = groupJoinedStreams(jobGraph);

    // Set partitions of intermediate streams if any
    if (!jobGraph.getIntermediateStreamEdges().isEmpty()) {
      new IntermediateStreamManager(config).calculatePartitions(jobGraph, joinedStreamSets);
    }

    // Verify every group of joined streams has the same partition count
    joinedStreamSets.forEach(ExecutionPlanner::validatePartitions);

    return jobGraph;
  }

  private void validateConfig() {
    ApplicationConfig appConfig = new ApplicationConfig(config);
    ClusterManagerConfig clusterConfig = new ClusterManagerConfig(config);
    // currently we don't support host-affinity in batch mode
    if (appConfig.getAppMode() == ApplicationConfig.ApplicationMode.BATCH && clusterConfig.getHostAffinityEnabled()) {
      throw new SamzaException(String.format("Host affinity is not supported in batch mode. Please configure %s=false.",
          ClusterManagerConfig.CLUSTER_MANAGER_HOST_AFFINITY_ENABLED));
    }
  }

  /**
   * Creates the physical graph from {@link ApplicationDescriptorImpl}
   */
  /* package private */
  JobGraph createJobGraph(ApplicationDescriptorImpl<? extends ApplicationDescriptor> appDesc, int i) {
//    JobGraph jobGraph = new JobGraph(config, appDesc);
//    String jobId = config.get(JobConfig.JOB_ID(), "1");
//
//    Set<StreamSpec> sourceStreams = getStreamSpecs(appDesc.getInputStreamIds(), streamConfig);
//    Set<StreamSpec> sinkStreams = getStreamSpecs(appDesc.getOutputStreamIds(), streamConfig);
//
//    // TODO: find and construct intermediate Streams
////    Set<StreamSpec> intermediateStreams = ;
//
//    // TODO: Create multi job nodes for the whole, check if need change in Job Context
//    // 1. Every Operator is a JobNode
//    // 2. Every Operator connect to each other using Intermediate Stream defined by StreamManager.
//    // 3. Operator has its own Job Context, AppDesc should be different.
//
//    // step1: Create jobNode
//    Set<String> operators = appDesc.getOperators();
//    Map<String, JobNode> nodes = new HashMap<>();
//    for (String operator : operators) {
//      if (operator.contains("input")) {
//        operator = "input";
//      }
//      if (operator.contains("send-to")) {
//        operator = "send_to";
//      }
//      nodes.put(operator, jobGraph.getOrCreateJobNode(operator, jobId));
//
//    }
//
//    // step2: Add Input Stream
//    sourceStreams.forEach(spec -> jobGraph.addInputStream(spec, nodes.get("input")));
//    // step3: Add Intermediate Stream
////    intermediateStreams.forEach(spec -> jobGraph.addIntermediateStream(spec, , ));
//    // step4: Add OutputStream
//    sinkStreams.forEach(spec -> jobGraph.addOutputStream(spec, nodes.get("send-to")));

//    -------------------------------------------------------------------------------------------

//    JobGraph jobGraph = new JobGraph(config, appDesc);
//    // Source streams contain both input and intermediate streams.
//    Set<StreamSpec> sourceStreams = getStreamSpecs(appDesc.getInputStreamIds(), streamConfig);
//    // Sink streams contain both output and intermediate streams.
//    Set<StreamSpec> sinkStreams = getStreamSpecs(appDesc.getOutputStreamIds(), streamConfig);
//
//    Set<StreamSpec> intermediateStreams = Sets.intersection(sourceStreams, sinkStreams);
//    Set<StreamSpec> inputStreams = Sets.difference(sourceStreams, intermediateStreams);
//    Set<StreamSpec> outputStreams = Sets.difference(sinkStreams, intermediateStreams);
//
//    Set<TableSpec> tables = appDesc.getTableDescriptors().stream()
//            .map(tableDescriptor -> ((BaseTableDescriptor) tableDescriptor).getTableSpec()).collect(Collectors.toSet());
//
//    // For this phase, we have a single job node for the whole DAG
//    String jobName = config.get(JobConfig.JOB_NAME());
//    String jobId = config.get(JobConfig.JOB_ID(), "1");
//
//    // create job nodes
//    Map<String, JobNode> nodes = new HashMap<>();
//    for (int i = 0; i < intermediateStreams.size()+1; i++) {
//      // clone appDesc to new object
//      ApplicationDescriptorImpl<? extends ApplicationDescriptor> jobNodeDesc = ApplicationDescriptorUtil.getAppDescriptor(appDesc.app, appDesc.getConfig());
//      // modify appDesc to jobNodeDesc
//      jobNodeDesc.splitAppDesc(i);
//      nodes.put("node" + i, jobGraph.getOrCreateJobNode("node" + i, jobId, jobNodeDesc));
//    }
//
//
//
//    // Add input streams
//    inputStreams.forEach(spec -> jobGraph.addInputStream(spec, nodes.get("node0")));
//
//    // Add output streams
//    outputStreams.forEach(spec -> jobGraph.addOutputStream(spec, nodes.get("node1")));
//
//    // Add intermediate streams
//    intermediateStreams.forEach(spec -> jobGraph.addIntermediateStream(spec, nodes.get("node0"), nodes.get("node1")));
//
//    // Add tables
//    for (TableSpec table : tables) {
//      jobGraph.addTable(table, nodes.get("node1"));
//      // Add side-input streams (if any)
//      Iterable<String> sideInputs = ListUtils.emptyIfNull(table.getSideInputs());
//      for (String sideInput : sideInputs) {
//        jobGraph.addSideInputStream(getStreamSpec(sideInput, streamConfig));
//      }
//    }

//    -------------------------------------------------------------------------------------------

    JobGraph jobGraph = new JobGraph(config, appDesc);
    // Source streams contain both input and intermediate streams.
    Set<StreamSpec> sourceStreams = getStreamSpecs(appDesc.getInputStreamIds(), streamConfig);
    // Sink streams contain both output and intermediate streams.
    Set<StreamSpec> sinkStreams = getStreamSpecs(appDesc.getOutputStreamIds(), streamConfig);

    Set<StreamSpec> intermediateStreams = Sets.intersection(sourceStreams, sinkStreams);
    Set<StreamSpec> inputStreams = Sets.difference(sourceStreams, intermediateStreams);
    Set<StreamSpec> outputStreams = Sets.difference(sinkStreams, intermediateStreams);

    Set<TableSpec> tables = appDesc.getTableDescriptors().stream()
            .map(tableDescriptor -> ((BaseTableDescriptor) tableDescriptor).getTableSpec()).collect(Collectors.toSet());

    // For this phase, we have a single job node for the whole DAG
    String jobName = config.get(JobConfig.JOB_NAME());
    String jobId = config.get(JobConfig.JOB_ID(), "1");
//    appDesc.splitAppDesc(jobName.substring(0, jobName.length() - 1), i);
    JobNode node = jobGraph.getOrCreateJobNode(jobName, jobId, appDesc);


    // Add input streams
    inputStreams.forEach(spec -> jobGraph.addInputStream(spec, node));

    // Add output streams
    outputStreams.forEach(spec -> jobGraph.addOutputStream(spec, node));

    // Add intermediate streams
    intermediateStreams.forEach(spec -> jobGraph.addIntermediateStream(spec, node, node));

    // Add tables
    for (TableSpec table : tables) {
      jobGraph.addTable(table, node);
      // Add side-input streams (if any)
      Iterable<String> sideInputs = ListUtils.emptyIfNull(table.getSideInputs());
      for (String sideInput : sideInputs) {
        jobGraph.addSideInputStream(getStreamSpec(sideInput, streamConfig));
      }
    }

    if (!LegacyTaskApplication.class.isAssignableFrom(appDesc.getAppClass())) {
      // skip the validation when input streamIds are empty. This is only possible for LegacyTaskApplication
      jobGraph.validate();
    }

    return jobGraph;
  }

  /**
   * Fetches the partitions of input, side-input, and output streams and updates their corresponding StreamEdges.
   */
  /* package private */ void setInputAndOutputStreamPartitionCount(JobGraph jobGraph) {
    Set<StreamEdge> existingStreams = new HashSet<>();
    existingStreams.addAll(jobGraph.getInputStreams());
    existingStreams.addAll(jobGraph.getSideInputStreams());
    existingStreams.addAll(jobGraph.getOutputStreams());

    // System to StreamEdges
    Multimap<String, StreamEdge> systemToStreamEdges = HashMultimap.create();

    // Group StreamEdges by system
    for (StreamEdge streamEdge : existingStreams) {
      String system = streamEdge.getSystemStream().getSystem();
      systemToStreamEdges.put(system, streamEdge);
    }

    // Fetch partition count for every set of StreamEdges belonging to a particular system.
    for (String system : systemToStreamEdges.keySet()) {
      Iterable<StreamEdge> streamEdges = systemToStreamEdges.get(system);

      // Map every stream to its corresponding StreamEdge so we can retrieve a StreamEdge given its stream.
      Map<String, StreamEdge> streamToStreamEdge = new HashMap<>();
      for (StreamEdge streamEdge : streamEdges) {
        streamToStreamEdge.put(streamEdge.getSystemStream().getStream(), streamEdge);
      }

      // Retrieve partition count for every set of streams.
      Set<String> streams = streamToStreamEdge.keySet();
      Map<String, Integer> streamToPartitionCount = streamManager.getStreamPartitionCounts(system, streams);

      // Retrieve StreamEdge corresponding to every stream and set partition count on it.
      for (Map.Entry<String, Integer> entry : streamToPartitionCount.entrySet()) {
        String stream = entry.getKey();
        Integer partitionCount = entry.getValue();
        streamToStreamEdge.get(stream).setPartitionCount(partitionCount);
        log.info("Fetched partition count value {} for stream {}", partitionCount, stream);
      }
    }
  }

  /**
   * Groups streams participating in joins together.
   */
  private static List<StreamSet> groupJoinedStreams(JobGraph jobGraph) {
    // Group input operator specs (input/intermediate streams) by the joins they participate in.
    Multimap<OperatorSpec, InputOperatorSpec> joinOpSpecToInputOpSpecs =
        OperatorSpecGraphAnalyzer.getJoinToInputOperatorSpecs(
            jobGraph.getApplicationDescriptorImpl().getInputOperators().values());

    // Convert every group of input operator specs into a group of corresponding stream edges.
    List<StreamSet> streamSets = new ArrayList<>();
    for (OperatorSpec joinOpSpec : joinOpSpecToInputOpSpecs.keySet()) {
      Collection<InputOperatorSpec> joinedInputOpSpecs = joinOpSpecToInputOpSpecs.get(joinOpSpec);
      StreamSet streamSet = getStreamSet(joinOpSpec.getOpId(), joinedInputOpSpecs, jobGraph);

      // If current join is a stream-table join, add the stream edges corresponding to side-input
      // streams associated with the joined table (if any).
      if (joinOpSpec instanceof StreamTableJoinOperatorSpec) {
        StreamTableJoinOperatorSpec streamTableJoinOperatorSpec = (StreamTableJoinOperatorSpec) joinOpSpec;

        Collection<String> sideInputs = ListUtils.emptyIfNull(streamTableJoinOperatorSpec.getTableSpec().getSideInputs());
        Iterable<StreamEdge> sideInputStreams = sideInputs.stream().map(jobGraph::getStreamEdge)::iterator;
        Iterable<StreamEdge> streams = streamSet.getStreamEdges();
        streamSet = new StreamSet(streamSet.getSetId(), Iterables.concat(streams, sideInputStreams));
      }

      streamSets.add(streamSet);
    }

    return Collections.unmodifiableList(streamSets);
  }

  /**
   * Creates a {@link StreamSet} whose Id is {@code setId}, and {@link StreamEdge}s
   * correspond to the provided {@code inputOpSpecs}.
   */
  private static StreamSet getStreamSet(String setId, Iterable<InputOperatorSpec> inputOpSpecs, JobGraph jobGraph) {
    Set<StreamEdge> streamEdges = new HashSet<>();
    for (InputOperatorSpec inputOpSpec : inputOpSpecs) {
      StreamEdge streamEdge = jobGraph.getStreamEdge(inputOpSpec.getStreamId());
      streamEdges.add(streamEdge);
    }
    return new StreamSet(setId, streamEdges);
  }

  /**
   * Verifies all {@link StreamEdge}s in the supplied {@code streamSet} agree in
   * partition count, or throws.
   */
  private static void validatePartitions(StreamSet streamSet) {
    Collection<StreamEdge> streamEdges = streamSet.getStreamEdges();
    StreamEdge referenceStreamEdge = streamEdges.stream().findFirst().get();
    int referencePartitions = referenceStreamEdge.getPartitionCount();

    for (StreamEdge streamEdge : streamEdges) {
      int partitions = streamEdge.getPartitionCount();
      if (partitions != referencePartitions) {
        throw  new SamzaException(String.format(
            "Unable to resolve input partitions of stream %s for the join %s. Expected: %d, Actual: %d",
            referenceStreamEdge.getName(), streamSet.getSetId(), referencePartitions, partitions));
      }
    }
  }

  /**
   * Represents a set of {@link StreamEdge}s.
   */
  /* package private */ static class StreamSet {

    private final String setId;
    private final Set<StreamEdge> streamEdges;

    StreamSet(String setId, Iterable<StreamEdge> streamEdges) {
      this.setId = setId;
      this.streamEdges = ImmutableSet.copyOf(streamEdges);
    }

    Set<StreamEdge> getStreamEdges() {
      return Collections.unmodifiableSet(streamEdges);
    }

    String getSetId() {
      return setId;
    }
  }
}
