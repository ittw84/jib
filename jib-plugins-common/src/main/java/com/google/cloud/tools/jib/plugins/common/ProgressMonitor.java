/*
 * Copyright 2018 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.events.ProgressEvent.ProgressAllocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class ProgressMonitor implements Consumer<ProgressEvent> {

  private final ExecutorService executorService;

  /** Maps from {@link ProgressAllocation} to number of units completed in that allocation. */
  private final Map<ProgressAllocation, Long> allocationCompletionMap = new HashMap<>();

  private double progress = 0.0;

  public ProgressMonitor(ExecutorService executorService) {
    this.executorService = executorService;
  }

  @Override
  public synchronized void accept(ProgressEvent progressEvent) {
    ProgressAllocation progressAllocation = progressEvent.getProgressAllocation();
    long progressUnits = progressEvent.getProgressUnits();
    long progressTotal = progressAllocation.getAllocationUnits();

    updateCompletionMap(progressAllocation, progressUnits);

    // TODO: Refactor
    if (progressUnits == 0) {
      return;
    }

    progress += progressUnits * progressAllocation.getFractionOfRoot() / progressTotal;

    displayProgress(50);
  }

  private void updateCompletionMap(ProgressAllocation progressAllocation, long progressUnits) {
    if (!allocationCompletionMap.containsKey(progressAllocation)) {
      allocationCompletionMap.put(progressAllocation, 0L);
    }
    if (progressUnits == 0) {
      return;
    }

    long priorCompleted = allocationCompletionMap.get(progressAllocation);
    long newCompleted = priorCompleted + progressUnits;
    if (newCompleted > progressAllocation.getAllocationUnits()) {
      throw new IllegalStateException("Progress exceeds max for '" + progressAllocation.getDescription() + "': " + newCompleted + " > " + progressAllocation.getAllocationUnits());
    }
    allocationCompletionMap.put(progressAllocation, newCompleted);

    // Updates the parents.
    if (newCompleted == progressAllocation.getAllocationUnits()) {
      progressAllocation.getParent().ifPresent(parentProgressAllocation -> {
        updateCompletionMap(parentProgressAllocation, 1);
      });
    }
  }

//  private List<String> getUnfinishedDescriptions() {
//    List<String> descriptions = new ArrayList<>();
//    for (Map.Entry<ProgressAllocation, Long> allocationCompletionEntry : allocationCompletionMap.entrySet()) {
//      ProgressAllocation progressAllocation = allocationCompletionEntry.getKey();
//      if (progressAllocation.getAllocationUnits() != allocationCompletionEntry.getValue()) {
//        descriptions.add(progressAllocation.getDescription());
//      }
//    }
//    return descriptions;
//  }

  private void displayProgress(int numBars) {
    StringBuilder progressLine = new StringBuilder();

    progressLine.append("Executing tasks [");
    int barsToDisplay = (int)Math.round(numBars * progress);
    for (int i = 0; i < numBars; i ++) {
      if (i < barsToDisplay) {
        progressLine.append('=');
      } else {
        progressLine.append(' ');
      }
    }
    progressLine.append("] ");
    progressLine.append(String.format("%.1f", progress * 100));
    progressLine.append("% complete");

    executorService.submit(() -> {
      System.out.print("\033[1A");
      System.out.println(progressLine);
    });
  }
}
