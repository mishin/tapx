// Copyright 2009 Formos
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.formos.tapestry.templating.internal;

public class TemplatingUtils
{
    /**
     * Invoked to indicate a method is not supported.
     *
     * @param <T>
     * @return never!
     * @throws UnsupportedOperationException describing the method name
     */
    public static <T> T notSupported()
    {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();

        int i = 0;
        while (!elements[i].getMethodName().equals("notSupported"))
            i++;

        throw new UnsupportedOperationException(
                String.format("Method %s() is not supported.", elements[i + 1].getMethodName()));
    }
}
