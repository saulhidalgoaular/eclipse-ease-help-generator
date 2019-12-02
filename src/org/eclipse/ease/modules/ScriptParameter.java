/*******************************************************************************
 * Copyright (c) 2014 Christian Pontesegger and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * Contributors:
 *     Christian Pontesegger - initial API and implementation
 *******************************************************************************/
package org.eclipse.ease.modules;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PARAMETER })
@Documented
public @interface ScriptParameter {

	public static final String NULL = "org.eclipse.ease.modules.ScriptParameter.null";

	public static final String UNDEFINED = "org.eclipse.ease.modules.ScriptParameter.undefined";

	String name() default "";

	String defaultValue() default UNDEFINED;

	public static class OptionalParameterHelper {

		protected static String getDefaultValue(final ScriptParameter in) {
			String defaultValue = in.defaultValue();
			if (defaultValue == null) {
				try {
					Method method = ScriptParameter.class.getMethod("defaultValue", null);
					Object defaultV = method.getDefaultValue();
					if (defaultV instanceof String) {
						defaultValue = (String) defaultV;
					}
				} catch (NoSuchMethodException e) {
					e.printStackTrace();
				} catch (SecurityException e) {
					e.printStackTrace();
				}
			}
			return defaultValue;
		}

		public static Object getDefaultValue(final ScriptParameter in, final Class<?> type) {
			String defaultValue = getDefaultValue(in);
			if ((defaultValue == null) || UNDEFINED.equals(defaultValue)) {
				return null;
			}
			if (Integer.class.isAssignableFrom(type)) {
				return Integer.getInteger(defaultValue);
			} else if (String.class.isAssignableFrom(type)) {
				return defaultValue;
			} else if (Boolean.class.isAssignableFrom(type)) {
				return Boolean.getBoolean(defaultValue);
			}
			return null;
		}
	}

	public static class Helper {
		public static boolean isOptional(final ScriptParameter parameter) {
			return !parameter.defaultValue().equals(ScriptParameter.UNDEFINED);
		}
	}
}
