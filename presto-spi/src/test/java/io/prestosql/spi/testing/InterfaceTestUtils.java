/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.spi.testing;

import com.google.common.collect.ImmutableSet;

import java.lang.reflect.Method;
import java.util.function.Function;

import static com.google.common.reflect.Reflection.newProxy;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public final class InterfaceTestUtils
{
    private InterfaceTestUtils() {}

    public static <I, C extends I> void assertAllMethodsOverridden(Class<I> iface, Class<C> clazz)
    {
        assertEquals(ImmutableSet.copyOf(clazz.getInterfaces()), ImmutableSet.of(iface));
        for (Method method : iface.getMethods()) {
            try {
                Method override = clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
                if (!method.getReturnType().isAssignableFrom(override.getReturnType())) {
                    fail(format("%s is not assignable from %s for method %s", method.getReturnType(), override.getReturnType(), method));
                }
            }
            catch (NoSuchMethodException e) {
                fail(format("%s does not override [%s]", clazz.getName(), method));
            }
        }
    }

    public static <I, C extends I> void assertProperForwardingMethodsAreCalled(Class<I> iface, Function<I, C> forwardingInstanceFactory)
    {
        for (Method actualMethod : iface.getDeclaredMethods()) {
            Object[] actualArguments = new Object[actualMethod.getParameterCount()];
            for (int i = 0; i < actualArguments.length; i++) {
                if (actualMethod.getParameterTypes()[i] == boolean.class) {
                    actualArguments[i] = false;
                }
            }
            C forwardingInstance = forwardingInstanceFactory.apply(
                    newProxy(iface, (proxy, expectedMethod, expectedArguments) -> {
                        assertEquals(actualMethod.getName(), expectedMethod.getName());
                        // TODO assert arguments

                        if (actualMethod.getReturnType() == boolean.class) {
                            return false;
                        }
                        return null;
                    }));

            try {
                if (!defines(forwardingInstance, actualMethod)) {
                    continue;
                }

                actualMethod.invoke(forwardingInstance, actualArguments);
            }
            catch (Exception e) {
                throw new RuntimeException(format("Invocation of %s has failed", actualMethod), e);
            }
        }
    }

    private static boolean defines(Object forwardingInstance, Method actualMethod)
            throws Exception
    {
        Class<?> forwardingClass = forwardingInstance.getClass();
        Method forwardingMethod = forwardingClass.getMethod(actualMethod.getName(), actualMethod.getParameterTypes());
        return forwardingClass == forwardingMethod.getDeclaringClass();
    }
}
