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

/**
 */
public class GCEEnvironmentConfig
{
  /**
   * targetWorkers: the number of workers to try to spawn at each call to provision
   * projectId: the id of the project where to operate
   * zoneName: the name of the zone where to operata
   * instanceTemplate: the template to use when creating the instances
   * minworkers: the minimum number of workers in the pool (*)
   * maxWorkers: the maximum number of workers in the pool (*)
   *
   * (*) both used by the caller of the AutoScaler to know if it makes sense to call
   *     provision / terminate or if there is no hope that something would be done
   */
  private final int targetWorkers;
  private final String projectId;
  private final String zoneName;
  private final String instanceTemplate;
  // used by the caller of the AutoScaler
  private final int minWorkers;
  private final int maxWorkers;

  @JsonCreator
  public GCEEnvironmentConfig(
          @JsonProperty("targetWorkers") int targetWorkers,
          @JsonProperty("minWorkers") int minWorkers,
          @JsonProperty("maxWorkers") int maxWorkers,
          @JsonProperty("projectId") String projectId,
          @JsonProperty("zoneName") String zoneName,
          @JsonProperty("instanceTemplate") String instanceTemplate
  )
  {
    this.targetWorkers = targetWorkers;
    this.minWorkers = minWorkers;
    this.maxWorkers = maxWorkers;
    this.projectId = projectId;
    this.zoneName = zoneName;
    this.instanceTemplate = instanceTemplate;
  }

  @JsonProperty
  public int getTargetWorkers()
  {
    return targetWorkers;
  }

  @JsonProperty
  public int getMinWorkers()
  {
    return minWorkers;
  }

  @JsonProperty
  public int getMaxWorkers()
  {
    return maxWorkers;
  }

  @Override
  public String toString()
  {
    return "GCEEnvironmentConfig{" +
            ", projectId=" + projectId +
            ", zoneName=" + zoneName +
            ", targetWorkers=" + targetWorkers +
            ", instanceTemplate=" + instanceTemplate +
            ", minWorkers=" + minWorkers +
            ", maxWorkers=" + maxWorkers +
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

    GCEEnvironmentConfig that = (GCEEnvironmentConfig) o;
    return (targetWorkers == that.targetWorkers &&
            projectId.equals(that.projectId) &&
            zoneName.equals(that.zoneName) &&
            instanceTemplate.equals(that.instanceTemplate) &&
            minWorkers == that.minWorkers &&
            maxWorkers == that.maxWorkers);
  }

  @Override
  public int hashCode()
  {
    int result = projectId != null ? projectId.hashCode() : 0;
    result = 31 * result + (zoneName != null ? zoneName.hashCode() : 0);
    result = 31 * result + (instanceTemplate != null ? instanceTemplate.hashCode() : 0);
    result = 31 * result + targetWorkers;
    result = 31 * result + minWorkers;
    result = 31 * result + maxWorkers;
    return result;
  }

  @JsonProperty
  String getZoneName()
  {
    return zoneName;
  }

  @JsonProperty
  String getProjectId()
  {
    return projectId;
  }

  @JsonProperty
  String getinstanceTemplate()
  {
    return instanceTemplate;
  }
}
