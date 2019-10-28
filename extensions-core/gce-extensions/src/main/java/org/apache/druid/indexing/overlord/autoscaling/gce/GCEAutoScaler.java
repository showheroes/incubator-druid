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


import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import org.apache.druid.indexing.overlord.autoscaling.AutoScaler;
import org.apache.druid.indexing.overlord.autoscaling.AutoScalingData;
import org.apache.druid.java.util.emitter.EmittingLogger;

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
import com.google.api.services.compute.model.Operation;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import static java.lang.String.format;

/**
 */
@JsonTypeName("gce")
public class GCEAutoScaler implements AutoScaler<GCEEnvironmentConfig>
{
  private static final EmittingLogger log = new EmittingLogger(GCEAutoScaler.class);

  private final GCEEnvironmentConfig envConfig;

  @JsonCreator
  public GCEAutoScaler(@JsonProperty("envConfig") GCEEnvironmentConfig envConfig) {
    this.envConfig = envConfig;
  }

  @Override
  @JsonProperty
  public int getMinNumWorkers()
  {
    return envConfig.getMinWorkers();
  }

  @Override
  @JsonProperty
  public int getMaxNumWorkers()
  {
    return envConfig.getMaxWorkers();
  }

  @Override
  @JsonProperty
  public GCEEnvironmentConfig getEnvConfig()
  {
    return envConfig;
  }

  public static Compute createComputeService() throws IOException, GeneralSecurityException {
    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    List<String> scopes = new ArrayList<>();

    scopes.add(ComputeScopes.COMPUTE);

    // Authenticate using Google Application Default Credentials.
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault().createScoped(scopes);
    HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

    return new Compute.Builder(httpTransport, jsonFactory, requestInitializer)
        .setApplicationName("VR-Druid-Autoscaler/0.1")
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
    final String instanceTemplate = envConfig.getinstanceTemplate();

    try {
      List<String> instanceIds = new LinkedList<>();
      Compute computeService = createComputeService();
      for (int i = 0; i < targetWorkers; ++i) {
        Instance instance = new Instance();
        Compute.Instances.Insert request =
                computeService.instances().insert(project, zone, instance);
        request.setSourceInstanceTemplate(instanceTemplate);
        Operation op = request.execute();
        if (op.getError() == null) {
          instanceIds.add(op.getName());
        }
        else {
          log.error("Unable to provision instance: %s", op.getError().toPrettyString());
        }
      }

      return new AutoScalingData(instanceIds);
    }
    catch (Exception e) {
      log.error(e, "Unable to provision any gce instances.");
    }

    return null;
  }

  /**
   * Terminats the
   */
  @Override
  public AutoScalingData terminate(List<String> ips)
  {
    if (ips.isEmpty()) {
      return new AutoScalingData(new ArrayList<>());
    }

    List<String> nodeIds = ipToIdLookup(ips);
    try {
      return terminateWithIds(nodeIds != null ? nodeIds: new LinkedList<>());
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
      List<String> deleted = new LinkedList<>();
      final String project = envConfig.getProjectId();
      final String zone = envConfig.getZoneName();
      Compute computeService = createComputeService();
      for(String id: ids) {
        Compute.Instances.Delete request = computeService.instances().delete(project, zone, id);
        Operation op = request.execute();
        if (op.getError() == null) {
          deleted.add(id);
        }
        else {
          log.error("Unable to terminate %s: %s", id, op.getError().toPrettyString());
        }
      }

      return new AutoScalingData(deleted);
    }
    catch (Exception e) {
      log.error(e, "Unable to terminate any instances.");
    }

    return null;
  }

  private String buildFilter(List<String> list, String key)
  {
    StringBuilder sb = new StringBuilder();
    Iterator<String> it = list.iterator();
    sb.append(format("(%s = \"%s\")", key, it.next()));
    while (it.hasNext()) {
      sb.append(" OR ").append(format("(%s = \"%s\")", key, it.next()));
    }
    return sb.toString();
  }

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

      List<String> instanceIds = new LinkedList<>();
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

      List<String> instanceIps = new LinkedList<>();
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
    return "gceAutoScaler{" +
        "envConfig=" + envConfig +
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

    if (envConfig != null ? !envConfig.equals(that.envConfig) : that.envConfig != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode()
  {
    int result = 0;
    result = 31 * result + (envConfig != null ? envConfig.hashCode() : 0);
    return result;
  }
}
