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

package org.apache.druid.indexing.overlord.autoscaling.gce;

import java.util.ArrayList;
import java.util.List;

import javax.xml.crypto.dsig.spec.XPathType.Filter;

import org.apache.druid.indexing.overlord.autoscaling.AutoScaler;
import org.apache.druid.indexing.overlord.autoscaling.AutoScalingData;
import org.apache.druid.indexing.overlord.autoscaling.SimpleWorkerProvisioningConfig;
import org.apache.druid.java.util.emitter.EmittingLogger;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.Reservation;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;

/**
 */
@JsonTypeName("ec2")
public class GCEAutoScaler implements AutoScaler<GCEEnvironmentConfig>
{
  private static final EmittingLogger log = new EmittingLogger(EC2AutoScaler.class);
  public static final int MAX_AWS_FILTER_VALUES = 100;

  private final int minNumWorkers;
  private final int maxNumWorkers;
  private final GCEEnvironmentConfig envConfig;
  private final SimpleWorkerProvisioningConfig config;

  @JsonCreator
  public GCEAutoScaler(
      @JsonProperty("minNumWorkers") int minNumWorkers,
      @JsonProperty("maxNumWorkers") int maxNumWorkers,
      @JsonProperty("envConfig") GCEEnvironmentConfig envConfig,
      @JacksonInject SimpleWorkerProvisioningConfig config
  )
  {
    this.minNumWorkers = minNumWorkers;
    this.maxNumWorkers = maxNumWorkers;
    this.envConfig = envConfig;
    this.config = config;
  }

  @Override
  @JsonProperty
  public int getMinNumWorkers()
  {
    return minNumWorkers;
  }

  @Override
  @JsonProperty
  public int getMaxNumWorkers()
  {
    return maxNumWorkers;
  }

  @Override
  @JsonProperty
  public GCEEnvironmentConfig getEnvConfig()
  {
    return envConfig;
  }

  @Override
  public AutoScalingData provision()
  {
	  try {
	  // Authenticate using Google Application Default Credentials.
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
      List<String> scopes = new ArrayList<>();

      scopes.add(ComputeScopes.COMPUTE);
      credentials = credentials.createScoped(scopes);
      HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

      // Create Compute Engine object for listing instances.
      Compute compute =
          new Compute.Builder(httpTransport, JSON_FACTORY, requestInitializer)
              .setApplicationName(envConfig.applicationName)
              .build();
      Compute.Instances.List instances = compute.instances().list(envConfig.projectId, envConfig.zoneName);
      InstanceList list = instances.execute();
      List<String> scopes = new ArrayList<>();
      final List<String> instanceIds = Lists.transform(
          list.getItems(),
          new Function<Instance, String>()
          {
            @Override
            public String apply(Instance input)
            {
              return input.getName();
            }
          }
      );

      log.info("Created instances: %s", instanceIds);
      return new AutoScalingData(instanceIds);
    }
    catch (Exception e) {
      log.error(e, "Unable to provision any GCE instances.");
    }

    return null;
  }

  @Override
  public AutoScalingData terminate(List<String> ips)
  {
    if (ips.isEmpty()) {
      return new AutoScalingData(new ArrayList<>());
    }

    DescribeInstancesResult result = amazonEC2Client.describeInstances(
        new DescribeInstancesRequest()
            .withFilters(
                new Filter("private-ip-address", ips)
            )
    );

    List<Instance> instances = new ArrayList<>();
    for (Reservation reservation : result.getReservations()) {
      instances.addAll(reservation.getInstances());
    }

    try {
      return terminateWithIds(
          Lists.transform(
              instances,
              new Function<Instance, String>()
              {
                @Override
                public String apply(Instance input)
                {
                  return input.getInstanceId();
                }
              }
          )
      );
    }
    catch (Exception e) {
      log.error(e, "Unable to terminate any instances.");
    }

    return null;
  }

  @Override
  public AutoScalingData terminateWithIds(List<String> ids)
  {
    if (ids.isEmpty()) {
      return new AutoScalingData(new ArrayList<>());
    }

    try {
      log.info("Terminating instances[%s]", ids);
      amazonEC2Client.terminateInstances(
          new TerminateInstancesRequest(ids)
      );

      return new AutoScalingData(ids);
    }
    catch (Exception e) {
      log.error(e, "Unable to terminate any instances.");
    }

    return null;
  }

  @Override
  public List<String> ipToIdLookup(List<String> ips)
  {
    final List<String> retVal = FluentIterable
        // chunk requests to avoid hitting default AWS limits on filters
        .from(Lists.partition(ips, MAX_AWS_FILTER_VALUES))
        .transformAndConcat(new Function<List<String>, Iterable<Reservation>>()
        {
          @Override
          public Iterable<Reservation> apply(List<String> input)
          {
            return amazonEC2Client.describeInstances(
                new DescribeInstancesRequest().withFilters(new Filter("private-ip-address", input))
            ).getReservations();
          }
        })
        .transformAndConcat(new Function<Reservation, Iterable<Instance>>()
        {
          @Override
          public Iterable<Instance> apply(Reservation reservation)
          {
            return reservation.getInstances();
          }
        })
        .transform(new Function<Instance, String>()
        {
          @Override
          public String apply(Instance instance)
          {
            return instance.getInstanceId();
          }
        }).toList();

    log.debug("Performing lookup: %s --> %s", ips, retVal);

    return retVal;
  }

  @Override
  public List<String> idToIpLookup(List<String> nodeIds)
  {
    final List<String> retVal = FluentIterable
        // chunk requests to avoid hitting default AWS limits on filters
        .from(Lists.partition(nodeIds, MAX_AWS_FILTER_VALUES))
        .transformAndConcat(new Function<List<String>, Iterable<Reservation>>()
        {
          @Override
          public Iterable<Reservation> apply(List<String> input)
          {
            return amazonEC2Client.describeInstances(
                new DescribeInstancesRequest().withFilters(new Filter("instance-id", input))
            ).getReservations();
          }
        })
        .transformAndConcat(new Function<Reservation, Iterable<Instance>>()
        {
          @Override
          public Iterable<Instance> apply(Reservation reservation)
          {
            return reservation.getInstances();
          }
        })
        .transform(new Function<Instance, String>()
        {
          @Override
          public String apply(Instance instance)
          {
            return instance.getPrivateIpAddress();
          }
        }).toList();

    log.debug("Performing lookup: %s --> %s", nodeIds, retVal);

    return retVal;
  }

  @Override
  public String toString()
  {
    return "EC2AutoScaler{" +
           "envConfig=" + envConfig +
           ", maxNumWorkers=" + maxNumWorkers +
           ", minNumWorkers=" + minNumWorkers +
           '}';
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    EC2AutoScaler that = (EC2AutoScaler) o;

    if (maxNumWorkers != that.maxNumWorkers) {
      return false;
    }
    if (minNumWorkers != that.minNumWorkers) {
      return false;
    }
    if (envConfig != null ? !envConfig.equals(that.envConfig) : that.envConfig != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode()
  {
    int result = minNumWorkers;
    result = 31 * result + maxNumWorkers;
    result = 31 * result + (envConfig != null ? envConfig.hashCode() : 0);
    return result;
  }
}
