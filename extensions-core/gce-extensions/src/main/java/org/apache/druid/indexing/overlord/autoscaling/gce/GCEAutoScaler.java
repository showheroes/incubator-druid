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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceGroupManagersDeleteInstancesRequest;
import com.google.api.services.compute.model.InstanceGroupManagersListManagedInstancesResponse;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.ManagedInstance;
import com.google.api.services.compute.model.Operation;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.apache.druid.indexing.overlord.autoscaling.AutoScaler;
import org.apache.druid.indexing.overlord.autoscaling.AutoScalingData;
import org.apache.druid.java.util.emitter.EmittingLogger;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 */
@JsonTypeName("gce")
public class GCEAutoScaler implements AutoScaler<GCEEnvironmentConfig>
{
  private static final EmittingLogger log = new EmittingLogger(GCEAutoScaler.class);

  private final GCEEnvironmentConfig envConfig;
  private final int minNumWorkers;
  private final int maxNumWorkers;

  @JsonCreator
  public GCEAutoScaler(
          @JsonProperty("minNumWorkers") int minNumWorkers,
          @JsonProperty("maxNumWorkers") int maxNumWorkers,
          @JsonProperty("envConfig") GCEEnvironmentConfig envConfig
  )
  {
    this.minNumWorkers = minNumWorkers;
    this.maxNumWorkers = maxNumWorkers;
    this.envConfig = envConfig;
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

  private Compute createComputeService() throws IOException, GeneralSecurityException
  {
    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    List<String> scopes = new ArrayList<>();

    scopes.add(ComputeScopes.COMPUTE);

    // Authenticate using Google Application Default Credentials.
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault().createScoped(scopes);
    HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

    return new Compute.Builder(httpTransport, jsonFactory, requestInitializer)
        .setApplicationName("Druid-Autoscaler/0.1")
        .build();
  }

  /**
   * When called, it tries to create envConfig.getTargetWorkers() instances at the time
   * using the template in envConfig.getinstanceTemplate()
   */
  @Override
  public AutoScalingData provision()
  {
    final String project = envConfig.getProjectId();
    final String zone = envConfig.getZoneName();
    final int targetWorkers = envConfig.getTargetWorkers();
    final String managedInstanceGroupName = envConfig.getManagedInstanceGroupName();

    try {
      List<String> before = getRunningInstances();
      int toSize = Math.min(before.size() + targetWorkers, getMaxNumWorkers());

      if (before.size() >= toSize) {
        // nothing to scale
        return new AutoScalingData(new ArrayList<>());
      }

      Compute computeService = createComputeService();
      Compute.InstanceGroupManagers.Resize request =
              computeService.instanceGroupManagers().resize(project, zone,
                      managedInstanceGroupName, toSize);

      Operation response = request.execute();
      response.wait(); // making the call blocking

      if (response.getError() == null) {
        List<String> after = getRunningInstances();
        after.removeAll(before); // these should be the new ones
        return new AutoScalingData(after);
      } else {
        log.error("Unable to provision instances: %s", response.getError().toPrettyString());
      }
    }
    catch (Exception e) {
      log.error(e, "Unable to provision any gce instances.");
    }

    return null;
  }

  /**
   * Terminats the instances in the list of IPs provided by the caller
   */
  @Override
  public AutoScalingData terminate(List<String> ips)
  {
    if (ips.isEmpty()) {
      return new AutoScalingData(new ArrayList<>());
    }

    List<String> nodeIds = ipToIdLookup(ips);
    try {
      return terminateWithIds(nodeIds != null ? nodeIds : new ArrayList<>());
    }
    catch (Exception e) {
      log.error(e, "Unable to terminate any instances.");
    }

    return null;
  }

  /**
   * Terminats the instances in the list of IDs provided by the caller
   */
  @Override
  public AutoScalingData terminateWithIds(List<String> ids)
  {
    if (ids.isEmpty()) {
      return new AutoScalingData(new ArrayList<>());
    }

    try {
      final String project = envConfig.getProjectId();
      final String zone = envConfig.getZoneName();
      final String managedInstanceGroupName = envConfig.getManagedInstanceGroupName();

      List<String> before = getRunningInstances();

      InstanceGroupManagersDeleteInstancesRequest requestBody =
              new InstanceGroupManagersDeleteInstancesRequest();
      requestBody.setInstances(ids);

      Compute computeService = createComputeService();
      Compute.InstanceGroupManagers.DeleteInstances request =
              computeService
                      .instanceGroupManagers()
                      .deleteInstances(project, zone, managedInstanceGroupName, requestBody);

      Operation response = request.execute();
      response.wait(); // making the call blocking

      if (response.getError() != null) {
        log.error("Unable to terminate instances: %s", response.getError().toPrettyString());
        List<String> after = getRunningInstances();
        before.removeAll(after); // keep only the ones no more present
        return new AutoScalingData(before);
      }

      return new AutoScalingData(ids);
    }
    catch (Exception e) {
      log.error(e, "Unable to terminate any instances.");
    }

    return null;
  }

  private List<String> getRunningInstances()
  {
    ArrayList<String> ids = new ArrayList<>();
    try {
      final String project = envConfig.getProjectId();
      final String zone = envConfig.getZoneName();
      final String managedInstanceGroupName = envConfig.getManagedInstanceGroupName();

      Compute computeService = createComputeService();
      Compute.InstanceGroupManagers.ListManagedInstances request =
              computeService
                      .instanceGroupManagers()
                      .listManagedInstances(project, zone, managedInstanceGroupName);

      InstanceGroupManagersListManagedInstancesResponse response = request.execute();

      for (ManagedInstance mi : response.getManagedInstances()) {
        ids.add(mi.getInstance());
      }
    }
    catch (Exception e) {
      log.error(e, "Unable to get instances.");
    }
    return ids;
  }

  private String buildFilter(List<String> list, String key)
  {
    Iterator<String> it = list.iterator();
    if (!it.hasNext()) {
      throw new IllegalArgumentException("List cannot be empty");
    }

    StringBuilder sb = new StringBuilder();
    sb.append(String.format(Locale.US, "(%s = \"%s\")", key, it.next()));
    while (it.hasNext()) {
      sb.append(" OR ").append(String.format(Locale.US, "(%s = \"%s\")", key, it.next()));
    }
    return sb.toString();
  }

  /**
   * Converts the IPs to IDs
   */
  @Override
  public List<String> ipToIdLookup(List<String> ips)
  {
    if (ips.isEmpty()) {
      return new ArrayList<>();
    }

    final String project = envConfig.getProjectId();
    final String zone = envConfig.getZoneName();

    try {
      Compute computeService = createComputeService();
      Compute.Instances.List request = computeService.instances().list(project, zone);
      request.setFilter(buildFilter(ips, "networkInterfaces.networkIP"));

      List<String> instanceIds = new ArrayList<>();
      InstanceList response;
      do {
        response = request.execute();
        if (response.getItems() == null) {
          continue;
        }
        for (Instance instance : response.getItems()) {
          instanceIds.add(instance.getName());
        }
        request.setPageToken(response.getNextPageToken());
      } while (response.getNextPageToken() != null);

      return instanceIds;
    }
    catch (Exception e) {
      log.error(e, "Unable to convert IPs to IDs.");
    }

    return null;
  }

  /**
   * Converts the IDs to IPs
   */
  @Override
  public List<String> idToIpLookup(List<String> nodeIds)
  {
    if (nodeIds.isEmpty()) {
      return new ArrayList<>();
    }

    final String project = envConfig.getProjectId();
    final String zone = envConfig.getZoneName();

    try {
      Compute computeService = createComputeService();
      Compute.Instances.List request = computeService.instances().list(project, zone);
      request.setFilter(buildFilter(nodeIds, "name"));

      List<String> instanceIps = new ArrayList<>();
      InstanceList response;
      do {
        response = request.execute();
        if (response.getItems() == null) {
          continue;
        }
        for (Instance instance : response.getItems()) {
          instanceIps.add(instance.getNetworkInterfaces().get(0).getNetworkIP());
        }
        request.setPageToken(response.getNextPageToken());
      } while (response.getNextPageToken() != null);

      return instanceIps;
    }
    catch (Exception e) {
      log.error(e, "Unable to convert IDs to IPs.");
    }

    return null;
  }

  @Override
  public String toString()
  {
    return "gceAutoScaler={" +
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

    GCEAutoScaler that = (GCEAutoScaler) o;

    return (envConfig != null ? envConfig.equals(that.envConfig) : that.envConfig == null) &&
            minNumWorkers == that.minNumWorkers &&
            maxNumWorkers == that.maxNumWorkers;
  }

  @Override
  public int hashCode()
  {
    int result = 0;
    result = 31 * result + (envConfig != null ? envConfig.hashCode() : 0);
    result = 31 * result + minNumWorkers;
    result = 31 * result + maxNumWorkers;
    return result;
  }
}
