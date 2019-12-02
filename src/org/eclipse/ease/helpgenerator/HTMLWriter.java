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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.lang.model.element.*;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementScanner9;

import com.sun.source.doctree.ThrowsTree;
import com.sun.source.util.DocTreeScanner;
import com.sun.source.util.DocTrees;
//import javax.lang.model.type.Type;

public class HTMLWriter {

	private class Overview implements Comparable<Overview> {
		private final String fTitle;
		private final String fLinkID;
		private final String fDescription;
		private final boolean fDeprecated;

		public Overview(final String title, final String linkID, final String description, final boolean deprecated) {
			fTitle = title;
			fLinkID = linkID;
			fDescription = description;
			fDeprecated = deprecated;
		}

		@Override
		public int compareTo(final Overview arg0) {
			return fTitle.compareTo(arg0.fTitle);
		}
	};

	private interface CommentExtractor {
		String extract(ExecutableElement method);
	};

	private static final String WRAP_TO_SCRIPT = "WrapToScript";
	private static final String QUALIFIED_WRAP_TO_SCRIPT = "org.eclipse.ease.modules." + WRAP_TO_SCRIPT;
	private static final Object SCRIPT_PARAMETER = "ScriptParameter";
	private static final Object QUALIFIED_SCRIPT_PARAMETER = "org.eclipse.ease.modules." + SCRIPT_PARAMETER;

	private static final String LINE_DELIMITER = "\n";

	private final LinkProvider fLinkProvider;
	private final Element fClazz;
	private final IMemento[] fDependencies;
	private final DocTrees fDocTrees;

	private final Collection<String> fDocumentationErrors = new ArrayList<>();

	/**
	 * A scanner to display the structure of a series of elements
	 * and their documentation comments.
	 */
	class ShowElements extends ElementScanner9<Void, Integer> {
		final PrintStream out;
		final DocTrees treeUtils;
		final String kind;
		final String name;
		final String fieldName;
		final int levelOfIndex;
		final int levelPosition;

		ShowElements(PrintStream out, DocTrees trees, String kind, String name, String fieldName,
					 int levelOfIndex, int levelPosition) {
			this.out = out;
			this.treeUtils = trees;
			this.kind = kind;
			this.name = name;
			this.fieldName = fieldName;
			this.levelOfIndex = levelOfIndex;
			this.levelPosition = levelPosition;
		}

		void show(Set<? extends Element> elements) {
			scan(elements, 0);
		}

		@Override
		public Void scan(Element e, Integer depth) {
			DocCommentTree dcTree = treeUtils.getDocCommentTree(e);
			if (dcTree != null && (kind == null || e.getKind().toString().equals(kind)) && (name == null || e.getSimpleName().toString().equals(name))) {
				//out.println(e.getKind() + " " + e);
				new ShowDocTrees(out, fieldName, levelOfIndex, levelPosition).scan(dcTree, depth + 1);
			}
			return super.scan(e, depth + 1);
		}
	}

	/**
	 * A scanner to display the structure of a documentation comment.
	 */
	static class ShowDocTrees extends DocTreeScanner<Void, Integer> {
		final PrintStream out;
		final String kind;
		final int levelToFind;
		private static int currentIndex;
		final int levelIndex;

		ShowDocTrees(PrintStream out, String kind, int levelToFind, int levelIndex) {
			this.out = out;
			this.kind = kind;
			this.levelToFind = levelToFind;
			this.levelIndex = levelIndex;
			currentIndex = 0;
		}

		@Override
		public Void scan(DocTree t, Integer depth) {
			if (t.getKind().toString().equals(kind)){
				++currentIndex;
			}
			String indent = "  ".repeat(depth);
			//if ( (kind == null || t.getKind().equals(kind)) && (name == null) )
			//if (t.getKind().toString().equals(kind)) {
			if ( (kind == null || t.getKind().toString().equals(kind)) &&
					(levelToFind == -1 || levelToFind == depth) &&
					(levelIndex == -1 || levelIndex == currentIndex)){
				out.println(t.toString());
			}
			/*System.out.println(indent + depth + "# "
					+ t.getKind() + " "
					+ t.toString().replace("\n", "\n" + indent + "#    "));*/
			return super.scan(t, depth + 1);
		}
	}

	public HTMLWriter(final Element clazz, final LinkProvider linkProvider, final IMemento[] dependencies, final DocTrees docTrees) {
		fClazz = clazz;
		fLinkProvider = linkProvider;
		fDependencies = dependencies;
		fDocTrees = docTrees;
	}

	private String getFullCommend(final List<? extends DocTree> bodyItems)
    {
        final StringBuilder builder = new StringBuilder();

        for (Object item : bodyItems){
            builder.append(item);
            builder.append("<br/>");
        }

        return builder.toString();
    }

	public String createContents(final String name) throws IOException {
		final StringBuffer buffer = new StringBuffer();

		addLine(buffer, "<html>");
		addLine(buffer, "<head>");
		addLine(buffer, "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>");
		addLine(buffer, "	<link rel=\"stylesheet\" type=\"text/css\" href=\"../../org.eclipse.ease.help/help/css/modules_reference.css\" />");
		addLine(buffer, "</head>");
		addLine(buffer, "<body>");
		addText(buffer, "	<div class=\"module\" title=\"");
		addText(buffer, name);
		addLine(buffer, " Module\">");

		// header
		addText(buffer, "		<h1>");
		addText(buffer, name);
		addLine(buffer, " Module</h1>");

		// class description
		addText(buffer, "		<p class=\"description\">");

		// TODO comment not available in ClassSymbol
		final String classComment = getFullCommend(fDocTrees.getDocCommentTree(fClazz).getFullBody());

		if ((classComment != null) && (!classComment.isEmpty()))
			addText(buffer, fLinkProvider.insertLinks(fClazz, classComment));

		else
			addDocumentationError("Missing class comment for " + fClazz.getSimpleName());

		addLine(buffer, "</p>");

		// dependencies
		addLine(buffer, createDependenciesSection());

		// end title div
		addLine(buffer, "	</div>");

		// constants
		addLine(buffer, createConstantsSection());

		// function overview
		addLine(buffer, createOverviewSection());

		// function details
		addLine(buffer, createDetailSection());

		addLine(buffer, "</body>");
		addLine(buffer, "</html>");

		return buffer.toString();
	}

	private String createDependenciesSection() {

		if (fDependencies.length > 0) {

			final StringBuffer buffer = new StringBuffer();
			addLine(buffer, "\t<h3>Dependencies</h3>");
			addLine(buffer, "\t<p>This module depends on following other modules which will automatically be loaded.</p>");
			addLine(buffer, "\t<ul class=\"dependency\">");

			for (final IMemento dependency : fDependencies)
				addLine(buffer, "\t\t<li>{@module " + dependency.getString("module") + "}</li>");

			addLine(buffer, "\t</ul>");

			return fLinkProvider.insertLinks(fClazz, buffer.toString());
		}

		return "";
	}

	private Object createDetailSection() throws IOException {
		final StringBuffer buffer = new StringBuffer();

		addLine(buffer, "\t<h2>Methods</h2>");

		for (final ExecutableElement method : getExportedMethods()) {
			// heading
			addText(buffer, "\t<div class=\"command");
			if (isDeprecated(method))
				addText(buffer, " deprecated");
			addText(buffer, "\" data-method=\"");
			addText(buffer, method.getSimpleName());
			addLine(buffer, "\">");

			addLine(buffer,
					"\t\t<h3" + (isDeprecated(method) ? " class=\"deprecatedText\"" : "") + "><a id=\"" + method.getSimpleName() + "\">" + method.getSimpleName() + "</a></h3>");

			// synopsis
			addLine(buffer, createSynopsis(method));

			// main description
			addLine(buffer, "\t\t<p class=\"description\">" + fLinkProvider.insertLinks(fClazz, getMethodComment(fClazz, method)) + "</p>");

			if (isDeprecated(method)) {
				String deprecationText = "This method is deprecated and might be removed in future versions.";

				addLine(buffer, "\t\t<p class=\"warning\"><b>Deprecation warning:</b> " + fLinkProvider.insertLinks(fClazz, deprecationText) + "</p>");
			}

			// aliases
			addLine(buffer, createAliases(method));

			// parameters
			addLine(buffer, createParametersArea(method));

			// return value
			addLine(buffer, createReturnValueArea(method));

			// declared exceptions
			addLine(buffer, createExceptionArea(method));

			// examples
			addLine(buffer, createExampleArea(method));

			addLine(buffer, "\t</div>");
		}

		return buffer;
	}

	private StringBuffer createExampleArea(final ExecutableElement method) {
		final StringBuffer buffer = new StringBuffer();

		final DocTree[] tags = new DocTree[0];//method.tags("scriptExample");
		if (tags.length > 0) {
			addLine(buffer, "		<dl class=\"examples\">");

			for (final DocTree tag : tags) {
				final String fullText = tag.toString();

				// extract end position of example code
				int pos = fullText.indexOf('(');
				if (pos > 0) {
					int open = 1;
					for (int index = pos + 1; index < fullText.length(); index++) {
						if (fullText.charAt(index) == ')')
							open--;
						else if (fullText.charAt(index) == '(')
							open++;

						if (open == 0) {
							pos = index + 1;
							break;
						}
					}
				}
				final String codeText = (pos > 0) ? fullText.substring(0, pos) : fullText;
				final String description = ((pos > 0) ? fullText.substring(pos).trim() : "");

				addLine(buffer, "			<dt>" + codeText + "</dt>");
				addText(buffer, "			<dd class=\"description\">" + fLinkProvider.insertLinks(fClazz, description));
				addLine(buffer, "</dd>");
			}

			addLine(buffer, "		</dl>");
		}

		return buffer;
	}

	private StringBuffer createReturnValueArea(final ExecutableElement method) throws IOException {
		final StringBuffer buffer = new StringBuffer();

		if (!"void".equals(method.getReturnType().toString())){
			addText(buffer, "		<p class=\"return\">");

			/*final DocTree[] tags = // method.tags("return");
			if (tags.length > 0) {
				if (tags[0].text().isEmpty())
					addDocumentationError("Missing return statement documentation for " + method.containingClass().name() + "." + method.name() + "()");

			}*/

			final Charset charset = StandardCharsets.UTF_8;
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			PrintStream printStream = new PrintStream(byteArrayOutputStream, true, charset.name());
			ShowElements showElements = new ShowElements(printStream, fDocTrees, ElementKind.METHOD.toString(), method.getSimpleName().toString(), "RETURN", 3, -1);
			showElements.scan(fClazz,0 );
			String comment = new String(byteArrayOutputStream.toByteArray());
			printStream.close();
			byteArrayOutputStream.close();

			final String prefix = "@return ";
			if ( comment.startsWith(prefix) ){
				comment = comment.substring(prefix.length());
			}
			addLine(buffer, comment);

			//addText(buffer, fLinkProvider.insertLinks(fClazz, method.getReturnType().toString()));

			addLine(buffer, "</p>");
		}

		return buffer;
	}

	private StringBuffer createParametersArea(final ExecutableElement method) {
		final StringBuffer buffer = new StringBuffer();

		if (!method.getParameters().isEmpty()) {

			addLine(buffer, "		<dl class=\"parameters\">");

			int currentParameter = 1;
			for (final VariableElement parameter : method.getParameters()) {
				addLine(buffer, "			<dt>" + parameter.getSimpleName() + "</dt>");
				addText(buffer, "			<dd class=\"description\" data-parameter=\"" + parameter.getSimpleName() + "\">"
						+ fLinkProvider.insertLinks(fClazz, getParameterComment(method, currentParameter)));

				++currentParameter;
				final AnnotationMirror parameterAnnotation = getScriptParameterAnnotation(parameter);
				if (parameterAnnotation != null) {
					addText(buffer, "<span class=\"optional\"><b>Optional:</b> defaults to &lt;<i>");
					for (final AnnotationValue pair : parameterAnnotation.getElementValues().values()) {
						if ("org.eclipse.ease.modules.ScriptParameter.defaultValue()".equals(pair.getValue().toString())) {
							String defaultValue = pair.getValue().toString();

							if ((!String.class.getName().equals(parameter.getKind().toString())) && (defaultValue.length() > 2))
								// remove quotes from default
								// value
								defaultValue = defaultValue.substring(1, defaultValue.length() - 1);

							if (defaultValue.contains("org.eclipse.ease.modules.ScriptParameter.null"))
								addText(buffer, "null");

							else
								addText(buffer, escapeText(defaultValue));
						}
					}
					addText(buffer, "</i>&gt;.</span>");
				}
				addLine(buffer, "</dd>");
			}
			addLine(buffer, "		</dl>");
		}

		return buffer;
	}

	private StringBuffer createExceptionArea(final ExecutableElement method) {
		final StringBuffer buffer = new StringBuffer();

		/*if (!method.getThrownTypes().isEmpty()) {

			addLine(buffer, "		<dl class=\"exceptions\">");

			for (final Type exceptionType : method.thrownExceptionTypes()) {
				addLine(buffer, "			<dt>" + exceptionType.simpleTypeName() + "</dt>");
				addText(buffer, "			<dd class=\"description\" data-exception=\"" + exceptionType.simpleTypeName() + "\">"
						+ fLinkProvider.insertLinks(fClazz, getExceptionComment(method, exceptionType)));

				addLine(buffer, "</dd>");
			}

			addLine(buffer, "		</dl>");
		}*/

		return buffer;
	}

	private String getExceptionComment(ExecutableElement method, Type exceptionType) {
		/*final String comment = extractComment(method, method1 -> {

			for (final ThrowsTree tag : method1.throwsTags()) {
				if ((exceptionType.simpleTypeName().equals(tag.exceptionName())) || (exceptionType.typeName().equals(tag.exceptionName())))
					return tag.exceptionComment();
			}

			return "";
		});

		if (comment.isEmpty())
			addDocumentationError(
					"Missing exception documentation for " + method.containingClass().name() + "." + method.name() + "() - " + exceptionType.simpleTypeName());

		return comment;*/
		return "";
	}

	private StringBuffer createAliases(final ExecutableElement method) {
		final StringBuffer buffer = new StringBuffer();

		final Collection<String> aliases = getFunctionAliases(method);
		if (!aliases.isEmpty()) {
			addLine(buffer, "		<p class=\"synonyms\"><em>Alias:</em>");

			for (final String alias : aliases)
				addText(buffer, " " + alias + "(),");

			// remove last comma
			buffer.deleteCharAt(buffer.length() - 1);

			addLine(buffer, "</p>");
		}

		return buffer;
	}

	private StringBuffer createSynopsis(final ExecutableElement method) {
		final StringBuffer buffer = new StringBuffer();

		addText(buffer, "		<p class=\"synopsis\">");
		addText(buffer, fLinkProvider.createClassText(LinkProvider.resolveClassName(method.getReturnType().toString(), fClazz)));
		addText(buffer, " ");
		addText(buffer, method.getSimpleName());
		addText(buffer, "(");
		int parameterIndex = 0;
		for (final VariableElement parameter : method.getParameters()) {
			final AnnotationMirror parameterAnnotation = getScriptParameterAnnotation(parameter);
			if (parameterAnnotation != null)
				addText(buffer, "[");

			String parameterType = "UNKNOWN";
			//parameterType = parameter.type;
			//parameterType = ((Symbol.VarSymbol) parameter).type.toString();
			String methodParameters = method.toString();
			methodParameters = methodParameters.substring(methodParameters.indexOf('(')+1, methodParameters.length() - 1);
			String [] parametersType = methodParameters.split(",");

			addText(buffer, fLinkProvider.createClassText(LinkProvider.resolveClassName(parametersType[parameterIndex], fClazz)));
			addText(buffer, " ");
			addText(buffer, parameter.getSimpleName());
			if (parameterAnnotation != null)
				addText(buffer, "]");

			addText(buffer, ", ");
			parameterIndex++;
		}
		if (method.getParameters().size() > 0)
			buffer.delete(buffer.length() - 2, buffer.length());

		addText(buffer, ")");
		addLine(buffer, "</p>");

		return buffer;
	}

	private StringBuffer createOverviewSection() throws IOException {
		final StringBuffer buffer = new StringBuffer();

		addLine(buffer, "	<h2>Method Overview</h2>");
		addLine(buffer, "	<table class=\"functions\">");
		addLine(buffer, "		<tr>");
		addLine(buffer, "			<th>Method</th>");
		addLine(buffer, "			<th>Description</th>");
		addLine(buffer, "		</tr>");

		final List<Overview> overview = new ArrayList<>();

		for (final ExecutableElement method : getExportedMethods()) {
			overview.add(new Overview(method.getSimpleName().toString(), method.getSimpleName().toString(), getMethodComment(fClazz, method), isDeprecated(method)));
			for (final String alias : getFunctionAliases(method))
				overview.add(
						new Overview(alias, method.getSimpleName().toString(), "Alias for <a href=\"#" + method.getSimpleName() + "\">" + method.getSimpleName() + "</a>.", isDeprecated(method)));
		}

		Collections.sort(overview);

		for (final Overview entry : overview) {
			addLine(buffer, "		<tr>");
			if (!entry.fDeprecated) {
				addLine(buffer, "			<td><a href=\"#" + entry.fLinkID + "\">" + entry.fTitle + "</a>()</td>");
				addLine(buffer, "			<td>" + fLinkProvider.insertLinks(fClazz, getFirstSentence(entry.fDescription)) + "</td>");

			} else {
				addLine(buffer, "			<td class=\"deprecatedText\"><a href=\"#" + entry.fLinkID + "\">" + entry.fTitle + "</a>()</td>");
				addLine(buffer, "			<td class=\"deprecatedDescription\"><b>Deprecated:</b> "
						+ fLinkProvider.insertLinks(fClazz, getFirstSentence(entry.fDescription)) + "</td>");
			}
			addLine(buffer, "		</tr>");
		}

		addLine(buffer, "	</table>");
		addLine(buffer, "");

		return buffer;
	}

	private String getMethodComment(Element baseClass, ExecutableElement method) throws IOException {
		final Charset charset = StandardCharsets.UTF_8;
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		PrintStream printStream = new PrintStream(byteArrayOutputStream, true, charset.name());
		ShowElements showElements = new ShowElements(printStream, fDocTrees, ElementKind.METHOD.toString(), method.getSimpleName().toString(), "TEXT", 3, -1);
		showElements.scan(fClazz,0 );
		String comment = new String(byteArrayOutputStream.toByteArray());
		printStream.close();
		byteArrayOutputStream.close();

		if (comment.isEmpty())
			addDocumentationError("Missing comment for " + baseClass.getSimpleName() + "." + method.getSimpleName() + "()");

		return comment;
	}

	private StringBuffer createConstantsSection() throws IOException {
		final StringBuffer buffer = new StringBuffer();

		final List<Element> fields = getExportedFields();
		if (!fields.isEmpty()) {
			addLine(buffer, "");
			addLine(buffer, "	<h2>Constants</h2>");
			addLine(buffer, "	<table class=\"constants\">");
			addLine(buffer, "		<tr>");
			addLine(buffer, "			<th>Constant</th>");
			addLine(buffer, "			<th>Description</th>");
			addLine(buffer, "		</tr>");

			for (final Element field : fields) {
				addLine(buffer, "\t\t<tr>");

				final Charset charset = StandardCharsets.UTF_8;
				ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
				PrintStream printStream = new PrintStream(byteArrayOutputStream, true, charset.name());
				ShowElements showElements = new ShowElements(printStream, fDocTrees, ElementKind.FIELD.toString(), field.getSimpleName().toString(), "DOC_COMMENT", -1, -1);
				showElements.scan(fClazz,0 );

				String content = new String(byteArrayOutputStream.toByteArray());
				printStream.close();
				byteArrayOutputStream.close();

				// TODO SAUL
				if (content.isEmpty())
					addDocumentationError("Field domentation missing for " + fClazz.getSimpleName() + "." + field.getSimpleName());

				if (!isDeprecated(field)) {
					addLine(buffer, "			<td><a id=\"" + field.getSimpleName() + "\">" + field.getSimpleName() + "</a></td>");
					addLine(buffer, "			<td class=\"description\" data-field=\"" + field.getSimpleName() + "\">"
							+ fLinkProvider.insertLinks(fClazz, content) + "</td>"); // TODO SAUL

				} else {
					addLine(buffer, "			<td><a id=\"" + field.getSimpleName() + "\" class=\"deprecatedText\">" + field.getSimpleName() + "</a></td>");
					addText(buffer, "			<td>" + fLinkProvider.insertLinks(fClazz, "")); // TODO SAUL
					String deprecationText = ""; //= field.tags("deprecated")[0].text();
					if (deprecationText.isEmpty())
						deprecationText = "This constant is deprecated and might be removed in future versions.";

					addText(buffer, "				<div class=\"warning\"><b>Deprecation warning:</b> " + fLinkProvider.insertLinks(fClazz, deprecationText)
							+ "</div>");
					addLine(buffer, "</td>");
				}

				addLine(buffer, "		</tr>");
			}

			addLine(buffer, "	</table>");
			addLine(buffer, "");
		}

		return buffer;
	}

	private Collection<String> getFunctionAliases(final ExecutableElement method) {
		final Collection<String> aliases = new HashSet<>();
		/*final AnnotationMirror annotation = getWrapAnnotation(method);
		if (annotation != null) {
			for (final AnnotationValue pair : annotation.elementValues()) {
				if ("alias".equals(pair.element().name())) {
					String candidates = pair.value().toString();
					candidates = candidates.substring(1, candidates.length() - 1);
					for (final String token : candidates.split("[,;]")) {
						if (!token.trim().isEmpty())
							aliases.add(token.trim());
					}
				}
			}
		}*/

		return aliases;
	}

	private static String getFirstSentence(final String description) {
		final int pos = description.indexOf('.');

		return (pos > 0) ? description.substring(0, pos + 1) : description;
	}

	private static void addText(final StringBuffer buffer, final Object text) {
		buffer.append(text);
	}

	private static void addLine(final StringBuffer buffer, final Object text) {
		buffer.append(text).append(LINE_DELIMITER);
	}

	private static boolean isDeprecated(final ExecutableElement method) {
		/*final DocTree[] tags = method.tags("deprecated");
		return (tags != null) && (tags.length > 0);*/
		return false;
	}

	private static boolean isDeprecated(final Element field) {
		// TODO SAUL
		/*final DocTree[] tags = field.tags("deprecated");
		return (tags != null) && (tags.length > 0);*/
		return false;
	}

	private static AnnotationMirror getScriptParameterAnnotation(final VariableElement parameter) {
		for (final AnnotationMirror annotation : parameter.getAnnotationMirrors()) {
			if (isScriptParameterAnnotation(annotation))
				return annotation;
		}

		return null;
	}

	private static boolean isScriptParameterAnnotation(final AnnotationMirror annotation) {
		return (QUALIFIED_SCRIPT_PARAMETER.equals(annotation.getAnnotationType().toString()))
				|| (SCRIPT_PARAMETER.equals(annotation.getAnnotationType().toString()));
	}

	private String getParameterComment(final ExecutableElement method, final int index) {
		String comment = "";
		try {
			final Charset charset = StandardCharsets.UTF_8;
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			PrintStream printStream = new PrintStream(byteArrayOutputStream, true, charset.name());

			ShowElements showElements = new ShowElements(printStream, fDocTrees, ElementKind.METHOD.toString(), method.getSimpleName().toString(), "PARAM", 3, index);
			showElements.scan(fClazz,0 );

			comment = new String(byteArrayOutputStream.toByteArray());
			final String prefix = "@param ";
			if ( comment.startsWith(prefix) ){
				comment.substring(prefix.length());
			}
			final int firstWord = comment.indexOf(' ');
			if (firstWord >= 0){
				comment = comment.substring(firstWord);
			}
			printStream.close();
			byteArrayOutputStream.close();

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return comment;
	}

	private String extractComment(ExecutableElement method, CommentExtractor extractor) {
		String comment = extractor.extract(method);
		if ((comment != null) && (!comment.isEmpty()))
			return comment;

		// try to look up interfaces
		/*for (final TypeElement iface : method.containingClass().interfaces()) {
			for (final ExecutableElement ifaceMethod : iface.methods()) {
				if (method.overrides(ifaceMethod)) {
					comment = extractComment(ifaceMethod, extractor);
					if ((comment != null) && (!comment.isEmpty()))
						return comment;
				}
			}
		}

		// not found, retry with super class
		final TypeElement parent = method.containingClass().superclass();
		if (parent != null) {
			for (final ExecutableElement superMethod : parent.methods()) {
				if (method.overrides(superMethod))
					return (extractComment(superMethod, extractor));
			}
		}*/

		return "";
	}

	private List<ExecutableElement> getExportedMethods() {
		final List<ExecutableElement> methods = new ArrayList<>();
		final boolean hasAnnotation = hasWrapToScriptAnnotation(fClazz);

		Element clazzDoc = fClazz;
		while ((clazzDoc != null) && (!Object.class.getName().equals(clazzDoc.getSimpleName()))) {
			for (final Element method : clazzDoc.getEnclosedElements()) {
				if ( ElementKind.METHOD.equals(method.getKind()) )
				{
					ExecutableElement executableElement = (ExecutableElement) method;
					if ((!hasAnnotation) || (getWrapAnnotation(method) != null))
						methods.add(executableElement);
				}
			}

			// TODO SAUL
			clazzDoc = null;
			//clazzDoc = clazzDoc.superclass();
		}

		// sort methods alphabetically
		Collections.sort(methods, Comparator.comparing(o -> o.getSimpleName().toString()));

		return methods;
	}

	private List<Element> getExportedFields() {
		final List<Element> fields = new ArrayList<>();

		final boolean hasAnnotation = hasWrapToScriptAnnotation(fClazz);

		final ArrayList<Element> candidates = new ArrayList<>();
		candidates.add(fClazz);
		while (!candidates.isEmpty()) {
			final Element clazzDoc = candidates.remove(0);

			for ( Element element : clazzDoc.getEnclosedElements() ){
				if ( ElementKind.FIELD.equals(element.getKind()) &&
						(!hasAnnotation || (getWrapAnnotation(element) != null))){
					fields.add(element);
				}

				// add interfaces
				if ( ElementKind.INTERFACE.equals(element.getKind()) )
				{
					candidates.add(element);
				}

				// TODO SAUL
				/*final ClassDoc nextCandidate = clazzDoc.superclass();
				if ((nextCandidate != null) && (!Object.class.getName().equals(nextCandidate.qualifiedName())))
					candidates.add(nextCandidate);*/
			}
		}

		// sort fields alphabetically
		Collections.sort(fields, Comparator.comparing(o -> o.getSimpleName().toString()));

		return fields;
	}

	private static boolean hasWrapToScriptAnnotation(Element clazzDoc) {
		while (clazzDoc != null) {
			final List<? extends Element> enclosedElements = clazzDoc.getEnclosedElements();
			for ( Element element : enclosedElements ){
				if (ElementKind.METHOD.equals(element.getKind()) ||
				    ElementKind.FIELD.equals(element.getKind())){
					if (getWrapAnnotation(element) != null){
						return true;
					}
				}
			}

			// TODO SAUL
			clazzDoc = null;
			//clazzDoc = clazzDoc.superclass();
		}

		return false;
	}

	private static AnnotationMirror getWrapAnnotation(final Element method) {
		for (final AnnotationMirror annotation : method.getAnnotationMirrors()) {
			if (isWrapToScriptAnnotation(annotation))
				return annotation;
		}

		return null;
	}

	private static boolean isWrapToScriptAnnotation(final AnnotationMirror annotation) {
		return (QUALIFIED_WRAP_TO_SCRIPT.equals(annotation.getAnnotationType().toString()))
				|| (WRAP_TO_SCRIPT.equals(annotation.getAnnotationType().toString()));
	}

	public static String escapeText(String text) {
		return text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
	}

	public Collection<String> getDocumentationErrors() {
		return fDocumentationErrors;
	}

	private void addDocumentationError(String message) {
		fDocumentationErrors.add(message);
	}
}
