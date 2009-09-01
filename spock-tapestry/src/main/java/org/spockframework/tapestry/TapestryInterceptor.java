/*
 * Copyright 2009 the original author or authors.
 *
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

package org.spockframework.tapestry;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import org.apache.tapestry5.ioc.*;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.ioc.annotations.SubModule;
import org.apache.tapestry5.ioc.services.MasterObjectProvider;

import org.spockframework.runtime.intercept.IMethodInterceptor;
import org.spockframework.runtime.intercept.IMethodInvocation;
import org.spockframework.runtime.model.SpeckInfo;
import org.spockframework.util.UnreachableCodeError;

import spock.lang.Shared;

public class TapestryInterceptor implements IMethodInterceptor {
  private final SpeckInfo speck;
  private Registry registry;

  public TapestryInterceptor(SpeckInfo speck) {
    this.speck = speck;
  }

  public void invoke(IMethodInvocation invocation) throws Throwable {
    switch(invocation.getMethod().getKind()) {
      case SETUP:
        invocation.proceed();
        injectServices(invocation.getTarget(), false);
        break;
      case SETUP_SPECK:
        startupRegistry();
        invocation.proceed();
        injectServices(invocation.getTarget(), true);
        break;
      case CLEANUP_SPECK:
        invocation.proceed();
        shutdownRegistry();
        break;
      default:
        throw new UnreachableCodeError();
    }
    
  }

  private void startupRegistry() {
    RegistryBuilder builder = new RegistryBuilder();
    builder.add(TapestrySupportModule.class);
    for (Class<?> module : getSubModules(speck)) builder.add(module);
    registry = builder.build();
    registry.performRegistryStartup();
  }

  private Class[] getSubModules(SpeckInfo speck) {
    SubModule modules = speck.getReflection().getAnnotation(SubModule.class);
    return modules == null ? new Class[0] : modules.value();
  }

  private void injectServices(Object target, boolean sharedFields) throws IllegalAccessException {
    MasterObjectProvider provider = registry.getService(MasterObjectProvider.class);
    for (final Field field : speck.getReflection().getDeclaredFields())
      if (field.isAnnotationPresent(Inject.class) && field.isAnnotationPresent(Shared.class) == sharedFields) {
        Object value = provider.provide(field.getType(), createAnnotationProvider(field), registry, true);
        field.setAccessible(true);
        field.set(target, value);
      }
  }

  private AnnotationProvider createAnnotationProvider(final Field field) {
    return new AnnotationProvider() {
      public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return field.getAnnotation(annotationClass);
      }
    };
  }

  private void shutdownRegistry() {
    if (registry != null) registry.shutdown();
  }
}