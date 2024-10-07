////
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

         https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
////

[#release-notes-${release.version?replace("[^a-zA-Z0-9]", "-", "r")}]
== ${release.version}

<#if release.date?has_content>Release date:: ${release.date}</#if>

This releases contains ...

=== GraalVM reachability metadata

Log4j Core and all its extension modules have been enhanced with embedded
https://www.graalvm.org/latest/reference-manual/native-image/metadata/[GraalVM reachability metadata].
This will allow the generation of GraalVM native images out-of-the-box, without any additional step necessary.

See our xref:graalvm.adoc[GraalVM guide] for more details.

=== ANSI support on Windows

Since 2017, Windows 10 and newer have offered native support for ANSI escapes.
The support for the outdated Jansi 1.x library has therefore been removed.
See xref:manual/pattern-layout.adoc#jansi[ANSI styling on Windows] for more information.

<#include "../.changelog.adoc.ftl">
