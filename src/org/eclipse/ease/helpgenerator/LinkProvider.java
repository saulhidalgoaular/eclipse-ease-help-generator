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
package org.eclipse.ease.helpgenerator;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;

/**
 * Collects registered packages and converts classes & API links to http anchors.
 */
public class LinkProvider {

	/** Pattern to detect a link token. */
	private static final Pattern PATTERN_LINK = Pattern.compile("\\{@(link|module)\\s+(.*?)\\}", Pattern.DOTALL);

	/** Pattern to parse a link. */
	private static final Pattern PATTERN_INNER_LINK = Pattern.compile("(\\w+(?:\\.\\w+)*)?(?:#(\\w+)(?:\\((.*?)\\))?)?");

	/** Maps (URL to use) -> Collection of package names. */
	private final Map<String, Collection<String>> fExternalDocs = new HashMap<>();

	public void registerAddress(final String location, final Collection<String> packages) {
		fExternalDocs.put(location, packages);
	}

	public static String resolveClassName(final String candidate, final Element clazz) {
		final String foundCandidate = findClass(candidate, clazz);
		return (foundCandidate != null) ? foundCandidate : candidate;
	}

	public String createClassText(String qualifiedName) {
		if (qualifiedName.contains(".")) {

			final String urlLocation = findClassURL(qualifiedName);
			if (urlLocation != null) {
				final String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));

				// first run, look for exact package match
				for (final Entry<String, Collection<String>> entry : fExternalDocs.entrySet()) {
					if (entry.getValue().contains(packageName))
						return "<a href=\"" + urlLocation + "\" title=\"" + HTMLWriter.escapeText(qualifiedName) + "\">"
								+ HTMLWriter.escapeText(qualifiedName.substring(packageName.length() + 1)) + "</a>";
				}

				// not found; try to locate matching parent package and hope for
				// the best
				for (final Entry<String, Collection<String>> entry : fExternalDocs.entrySet()) {
					for (final String entryPackage : entry.getValue()) {
						if (packageName.startsWith(entryPackage))
							return "<a href=\"" + urlLocation + "\" title=\"" + HTMLWriter.escapeText(qualifiedName) + "\">"
									+ HTMLWriter.escapeText(qualifiedName.substring(packageName.length() + 1)) + "</a>";
					}
				}

			} else
				qualifiedName = HTMLWriter.escapeText(qualifiedName);

		} else
			qualifiedName = HTMLWriter.escapeText(qualifiedName);

		return qualifiedName;
	}

	private static String findClass(final String name, final Element baseClass) {
		/*try {

			for (final TypeElement doc : baseClass.importedClasses()) {
				if (doc.toString().endsWith(name))
					return doc.toString();
			}
		} catch (final NullPointerException e) {
			// sometimes thrown by ClassDoc.importedClasses(). Nothing we can do here but ignore
		}*/

		/*final TypeElement target = new TypeElement() {
            @Override
            public List<? extends Element> getEnclosedElements() {
                return null;
            }

            @Override
            public NestingKind getNestingKind() {
                return null;
            }

            @Override
            public Name getQualifiedName() {
                return null;
            }

            @Override
            public Name getSimpleName() {
                return null;
            }

            @Override
            public TypeMirror getSuperclass() {
                return null;
            }

            @Override
            public List<? extends TypeMirror> getInterfaces() {
                return null;
            }

            @Override
            public List<? extends TypeParameterElement> getTypeParameters() {
                return null;
            }

            @Override
            public Element getEnclosingElement() {
                return null;
            }

            @Override
            public TypeMirror asType() {
                return null;
            }

            @Override
            public ElementKind getKind() {
                return null;
            }

            @Override
            public Set<Modifier> getModifiers() {
                return null;
            }

            @Override
            public List<? extends AnnotationMirror> getAnnotationMirrors() {
                return null;
            }

            @Override
            public <A extends Annotation> A getAnnotation(Class<A> aClass) {
                return null;
            }

            @Override
            public <R, P> R accept(ElementVisitor<R, P> elementVisitor, P p) {
                return null;
            }

            @Override
            public <A extends Annotation> A[] getAnnotationsByType(Class<A> aClass) {
                return null;
            }
        };//baseClass.findClass(name);
		return (target != null) ? target.toString() : null;*/
		return name;
	}

	private String findClassURL(String qualifiedName) {
		if (qualifiedName.contains("<"))
			qualifiedName = qualifiedName.substring(0, qualifiedName.indexOf("<"));

		if (qualifiedName.contains(".")) {
			final String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));

			// first run, look for exact package match
			for (final Entry<String, Collection<String>> entry : fExternalDocs.entrySet()) {
				if (entry.getValue().contains(packageName))
					return entry.getKey() + "/" + qualifiedName.replace('.', '/') + ".html";
			}

			// not found; try to locate matching parent package and hope for the
			// best
			for (final Entry<String, Collection<String>> entry : fExternalDocs.entrySet()) {
				for (final String entryPackage : entry.getValue()) {
					if (packageName.startsWith(entryPackage))
						return entry.getKey() + "/" + qualifiedName.replace('.', '/') + ".html";
				}
			}
		}

		return null;
	}

	public String insertLinks(final Element clazz, final String text) {

		final StringBuilder output = new StringBuilder();
		int startPos = 0;
		final Matcher matcher = PATTERN_LINK.matcher(text);

		while (matcher.find()) {
			output.append(text.substring(startPos, matcher.start()));
			startPos = matcher.end();

			final Matcher linkMatcher = PATTERN_INNER_LINK.matcher(matcher.group(2).replace('\r', ' ').replace('\n', ' '));
			if (linkMatcher.matches()) {
				// group 1 = class
				// group 2 = method (optional)
				// group 3 = params (without parenthesis)

				if ("link".equals(matcher.group(1))) {
					// link to java API

					final StringBuilder link = new StringBuilder();
					if (linkMatcher.group(2) != null) {
						link.append("#");

						link.append(linkMatcher.group(2));
						if (linkMatcher.group(3) != null) {
							link.append("-");

							for (String parameter : linkMatcher.group(3).split(",")) {
								parameter = parameter.trim().replace(" ", "");
								if (parameter.endsWith("]"))
									link.append(findClass(parameter.substring(0, parameter.indexOf('[')), clazz));
								else
									link.append(findClass(parameter, clazz));

								while (parameter.endsWith("]")) {
									link.append(":A");
									parameter = parameter.substring(0, parameter.lastIndexOf('[')).trim();
								}
								link.append("-");
							}

							if (link.charAt(link.length() - 1) != '-')
								link.append("-");
						}
					}

					if (linkMatcher.group(1) == null) {
						// link to same document
						output.append("<a href=\"" + link + "\">" + linkMatcher.group(2)
								+ ((linkMatcher.group(3) != null) ? "(" + linkMatcher.group(3) + ")" : "") + "</a>");
					} else {
						// external document

						final String classURL = findClassURL(resolveClassName(linkMatcher.group(1), clazz));
						if (classURL != null)
							output.append("<a href=\"" + classURL + link + "\">");

						output.append(linkMatcher.group(1));

						if (linkMatcher.group(2) != null) {
							output.append(linkMatcher.group(2));
							if (linkMatcher.group(3) != null) {
								output.append('(');
								output.append(linkMatcher.group(3));
								output.append(')');
							}
						}

						if (classURL != null)
							output.append("</a>");
					}

				} else if ("module".equals(matcher.group(1))) {
					// link to a scripting module
					if (linkMatcher.group(1) == null) {
						// link to same document
						output.append(
								"<a href=\"#" + linkMatcher.group(2) + "\">" + linkMatcher.group(2) + ((linkMatcher.group(3) != null) ? "()" : "") + "</a>");
					} else {
						// external document
						final String plugin = linkMatcher.group(1).substring(0, linkMatcher.group(1).lastIndexOf('.'));
						if (linkMatcher.group(2) != null)
							output.append("<a href=\"../../" + plugin + "/help/" + ModuleDoclet.createHTMLFileName(linkMatcher.group(1)) + "#"
									+ linkMatcher.group(2) + "\">" + linkMatcher.group(2) + ((linkMatcher.group(3) != null) ? "()" : "") + "</a>");
						else
							output.append("<a href=\"../../" + plugin + "/help/" + ModuleDoclet.createHTMLFileName(linkMatcher.group(1)) + "\">"
									+ capitalizeFirst(linkMatcher.group(1).substring(linkMatcher.group(1).lastIndexOf('.') + 1)) + " module</a>");
					}
				}
			}
		}

		if (startPos == 0)
			return text;

		output.append(text.substring(startPos));

		return output.toString();
	}

	private static String capitalizeFirst(final String content) {
		if (!content.isEmpty())
			return content.substring(0, 1).toUpperCase() + content.substring(1);

		return content;
	}
}
