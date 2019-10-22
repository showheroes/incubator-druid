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
public class GCENodeData
{
  private final int targetWorkers;
  private final String applicationName;
  private final String projectId;
  private final String zoneName;
  private final String instanceGroupManager;

  @JsonCreator
  public GCENodeData(
      @JsonProperty("targetWorkers") int targetWorkers,
      @JsonProperty("applicationName") String applicationName,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("zoneName") String zoneName,
      @JsonProperty("instanceGroupManager") String instanceGroupManager
      )
  {
    this.targetWorkers = targetWorkers;
    this.applicationName = applicationName;
    this.projectId = projectId;
    this.zoneName = zoneName;
    this.instanceGroupManager = instanceGroupManager;
  }

  @JsonProperty
  public int getTargetWorkers()
  {
    return targetWorkers;
  }

  @Override
  public String toString()
  {
    return "GCENodeData{" +
        ", projectId=" + projectId +
        ", applicationName=" + applicationName +
        ", zoneName=" + zoneName +
        ", targetWorkers=" + targetWorkers +
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

    GCENodeData that = (GCENodeData) o;

    if (targetWorkers != that.targetWorkers) {
      return false;
    }
    if (applicationName != that.applicationName) {
      return false;
    }
    if (projectId != that.projectId) {
      return false;
    }
    if (zoneName != that.zoneName) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode()
  {
    int result = projectId != null ? projectId.hashCode() : 0;
    result = 31 * result + (applicationName != null ? applicationName.hashCode() : 0);
    result = 31 * result + (zoneName != null ? zoneName.hashCode() : 0);
    result = 31 * result + (instanceGroupManager != null ? instanceGroupManager.hashCode() : 0);
    result = 31 * result + targetWorkers;
    return result;
  }

  public String getZoneName()
  {
    return zoneName;
  }

  public String getProjectId()
  {
    return projectId;
  }

  public String getApplicationName()
  {
    return applicationName;
  }

  public String getInstanceGroupManager()
  {
    return instanceGroupManager;
  }
}
