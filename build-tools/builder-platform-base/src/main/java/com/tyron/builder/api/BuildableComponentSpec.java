/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tyron.builder.api;

import com.tyron.builder.platform.base.ComponentSpec;

import javax.annotation.Nullable;

/**
 * A {@link ComponentSpec} that is directly {@link Buildable} via a specified task.
 */
@Incubating
public interface BuildableComponentSpec extends Buildable, ComponentSpec {
    /**
     * Returns the task responsible for building this component.
     */
    @Nullable
    Task getBuildTask();

    /**
     * Specifies the task responsible for building this component.
     */
    void setBuildTask(@Nullable Task buildTask);

    /**
     * Adds tasks required to build this component. Tasks added this way are subsequently
     * added as dependencies of this component's {@link #getBuildTask() build task}.
     */
    void builtBy(Object... tasks);

    boolean hasBuildDependencies();
}