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

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.spi.ToolProvider;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.util.DocTreeScanner;
import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.doclet.Doclet;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.util.ElementScanner9;
import javax.tools.DocumentationTool;

import jdk.javadoc.doclet.DocletEnvironment;

import static javax.tools.ToolProvider.getSystemDocumentationTool;

public class ModuleDoclet implements Doclet {

	/**
	 * Method to locally test this doclet. Not needed for productive use.
	 */
	public static void main(final String[] args) {
        String docletProjectRootDir = new File(System.getProperty("user.dir")).getAbsolutePath();
        docletProjectRootDir = docletProjectRootDir.replaceAll("\\\\", "/");

        String repositoryRootDir = new File(System.getProperty("user.dir")).getParentFile().getParent();
        repositoryRootDir = repositoryRootDir.replaceAll("\\\\", "/");

        final String projectDir = repositoryRootDir + "/../org.eclipse.ease.modules/plugins/org.eclipse.ease.modules.platform";

        // @formatter:off
        final String[] javadocargs = {
                "-sourcepath", "/home/saul/eclipse-workspace/debrief/org.mwc.debrief.scripting/src:/home/saul/eclipse-workspace/debrief/org.mwc.cmap.legacy/src:/home/saul/eclipse-workspace/debrief/org.mwc.debrief.legacy/src",
                "-root", "/home/saul/eclipse-workspace/debrief/org.mwc.debrief.scripting",
                "-doclet", ModuleDoclet.class.getName(),
                "-docletpath",  docletProjectRootDir + "/lib",

                "-failOnHTMLError", "false",
                "-failOnMissingDocs", "false",

                "-linkoffline", "https://docs.oracle.com/en/java/javase/11/docs/api/", "package-list",
				//"-link", "https://docs.oracle.com/javase/8/docs/api",
				"--ignore-source-errors",

                "-subpackages",
                "org.mwc.debrief.scripting.wrappers"
        };
		// @formatter:on

		final DocumentationTool systemDocumentationTool = getSystemDocumentationTool();
		final DocumentationTool.DocumentationTask toolTask = systemDocumentationTool.getTask(null, null, null, ModuleDoclet.class, List.of(javadocargs), null);
		toolTask.call();
	}

	private static final String OPTION_PROJECT_ROOT = "-root";
	private static final Object OPTION_LINK = "-link";
	private static final Object OPTION_LINK_OFFLINE = "-linkoffline";
	private static final Object OPTION_FAIL_ON_HTML_ERRORS = "-failOnHTMLError";
	private static final Object OPTION_FAIL_ON_MISSING_DOCS = "-failOnMissingDocs";

	public static SourceVersion languageVersion() {
		return SourceVersion.RELEASE_11;
	}

	public static int optionLength(final String option) {
		if (OPTION_PROJECT_ROOT.equals(option))
			return 2;

		if (OPTION_LINK.equals(option))
			return 2;

		if (OPTION_LINK_OFFLINE.equals(option))
			return 3;

		if (OPTION_FAIL_ON_HTML_ERRORS.equals(option))
			return 2;

		if (OPTION_FAIL_ON_MISSING_DOCS.equals(option))
			return 2;

		if ("-encoding".equals(option))
			return 2;

		if ("-protected".equals(option))
			return 1;

		if ("-author".equals(option))
			return 1;

		if ("-bottom".equals(option))
			return 2;

		if ("-charset".equals(option))
			return 2;

		if ("-docencoding".equals(option))
			return 2;

		if ("-doctitle".equals(option))
			return 2;

		if ("-windowtitle".equals(option))
			return 2;

		if ("-d".equals(option))
			return 2;

		if ("-use".equals(option))
			return 1;

		if ("-version".equals(option))
			return 1;

		return 0;
	}

	public static boolean validOptions(final String options[][], final Reporter reporter) {
		return true;
	}

	/** Maps module.class.name to module definition XML memento. */
	private Map<String, IMemento> fModuleNodes;
	private File fRootFolder = null;
	private final Collection<IMemento> fCategoryNodes = new HashSet<>();

	private LinkProvider fLinkProvider = new LinkProvider();
	private boolean fFailOnHTMLErrors = true;
	private boolean fFailOnMissingDocs = false;

	private Reporter reporter;

	private boolean process(final DocletEnvironment root) {
		final Set<? extends Element> elements = root.getIncludedElements();

		// write to output file
		if (fRootFolder != null) {
			try {
				// create lookup table with module data
				createModuleLookupTable();

				// create HTML help files
				boolean created = createHTMLFiles(root.getDocTrees(), elements);

				// create category TOCs
				created |= createCategories();

				if (created) {
					// some files were created, update project, ...

					// create module TOC files
					final Set<String> tocFiles = createModuleTOCFiles();

					// update plugin.xml
					updatePluginXML(fRootFolder, tocFiles);

					// update MANIFEST.MF
					updateManifest(fRootFolder);

					// update build.properties
					updateBuildProperties(fRootFolder);
				}
			} catch (final Exception e) {
				e.printStackTrace();
				return false;
			}

			return true;
		}

		return false;
	}

	private static Collection<String> parsePackages(final InputStream inputStream) {
		final Collection<String> packages = new HashSet<>();

		final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		try {
			String line = reader.readLine();
			while (line != null) {
				packages.add(line);
				line = reader.readLine();
			}
		} catch (final IOException e) {
			// could not read, ignore
		}

		return packages;
	}

	private boolean createCategories() throws IOException {
		boolean created = false;

		for (final IMemento node : fCategoryNodes) {
			final XMLMemento memento = XMLMemento.createWriteRoot("toc");
			memento.putString("label", node.getString("name"));
			memento.putString("link_to", createCategoryLink(node.getString("parent")));

			final IMemento topicNode = memento.createChild("topic");
			topicNode.putString("label", node.getString("name"));
			topicNode.putBoolean("sort", true);
			topicNode.createChild("anchor").putString("id", "modules_anchor");

			final File targetFile = getChild(getChild(fRootFolder, "help"), createCategoryFileName(node.getString("id")));
			writeFile(targetFile, memento.toString());
			created = true;
		}

		return created;
	}

	private static String extractCategoryName(final String categoryId) {
		if (categoryId != null) {
			final int index = categoryId.indexOf(".category.");
			if (index != -1)
				return categoryId.substring(index + ".category.".length());
		}

		return null;
	}

	private static String createCategoryLink(final String categoryId) {
		String pluginID = "org.eclipse.ease.help";
		if (categoryId != null) {
			final int index = categoryId.indexOf(".category.");
			if (index != -1)
				pluginID = categoryId.substring(0, index);
		}

		return "../" + pluginID + "/help/" + createCategoryFileName(categoryId) + "#modules_anchor";
	}

	private static String createCategoryFileName(final String categoryId) {
		final String category = extractCategoryName(categoryId);
		return (category != null) ? "category_" + category + ".xml" : "reference.xml";
	}

	private File getChild(final File folder, final String name) {
		// if the folder exists, it needs to be a directory
		// if it does not exist, it will be created by the writeFile() method
		if ((folder.isDirectory()) || (!folder.exists()))
			return new File(folder.getPath() + File.separator + name);

		return null;
	}

	private void updateManifest(final File rootFolder) throws IOException {
		final File manifestFile = getChild(getChild(rootFolder, "META-INF"), "MANIFEST.MF");

		final Manifest manifest = new Manifest();
		manifest.read(new FileInputStream(manifestFile));

		final Attributes mainAttributes = manifest.getMainAttributes();
		final String require = mainAttributes.getValue("Require-Bundle");

		if ((require == null) || (require.isEmpty()))
			mainAttributes.putValue("Require-Bundle", "org.eclipse.help;bundle-version=\"[3.5.0,4.0.0)\"");

		else if (!require.contains("org.eclipse.help"))
			mainAttributes.putValue("Require-Bundle", "org.eclipse.help;bundle-version=\"[3.5.0,4.0.0)\"," + require);

		else
			// manifest contains reference to org.eclipse.help, bail out
			return;

		final FileOutputStream out = new FileOutputStream(manifestFile);
		manifest.write(out);
		out.close();
	}

	private void updateBuildProperties(final File rootFolder) throws IOException {
		final File buildFile = getChild(rootFolder, "build.properties");

		final Properties properties = new Properties();
		properties.load(new FileInputStream(buildFile));
		final String property = properties.getProperty("bin.includes");
		if (!property.contains("help/")) {
			if (property.trim().isEmpty())
				properties.setProperty("bin.includes", "help/");
			else
				properties.setProperty("bin.includes", "help/," + property.trim());

			final FileOutputStream out = new FileOutputStream(buildFile);
			properties.store(out, "");
			out.close();
		}
	}

	private void updatePluginXML(final File rootFolder, final Collection<String> tocs) throws Exception {
		final HashSet<String> toDo = new HashSet<>(tocs);

		File pluginFile = getChild(rootFolder, "plugin.xml");
		if (!pluginFile.exists())
			pluginFile = getChild(rootFolder, "fragment.xml");

		final XMLMemento memento = XMLMemento.createReadRoot(new InputStreamReader(new FileInputStream(pluginFile)));
		for (final IMemento extensionNode : memento.getChildren("extension")) {
			final String extensionPoint = extensionNode.getString("point");
			if ("org.eclipse.help.toc".equals(extensionPoint)) {
				// a help topic is already registered
				for (final IMemento tocNode : extensionNode.getChildren("toc")) {
					final String tocLocation = tocNode.getString("file");
					if (tocLocation.length() > 5)
						toDo.remove(tocLocation.substring(5));
				}
			}
		}

		for (final String fileLocation : toDo) {
			// some TOCs not registered yet
			final IMemento extensionNode = memento.createChild("extension");
			extensionNode.putString("point", "org.eclipse.help.toc");
			final IMemento tocNode = extensionNode.createChild("toc");
			tocNode.putString("file", "help/" + fileLocation);
			tocNode.putBoolean("primary", false);

		}

		if (!toDo.isEmpty())
			// we had to modify the file
			writeFile(pluginFile, memento.toString().replace("&#x0A;", "\n"));
	}

	private Set<String> createModuleTOCFiles() throws IOException {
		final Map<String, IMemento> tocDefinitions = new HashMap<>();

		// create categories
		for (final IMemento categoryDefinition : fCategoryNodes) {
			final XMLMemento memento = XMLMemento.createWriteRoot("toc");
			memento.putString("label", categoryDefinition.getString("name"));
			memento.putString("link_to", createCategoryLink(categoryDefinition.getString("parent")));

			final IMemento topicNode = memento.createChild("topic");
			topicNode.putString("label", categoryDefinition.getString("name"));
			topicNode.putBoolean("sort", true);

			topicNode.createChild("anchor").putString("id", "modules_anchor");
			tocDefinitions.put(createCategoryFileName(categoryDefinition.getString("id")), memento);
		}

		// create modules
		if (!fModuleNodes.isEmpty()) {

			for (final IMemento moduleDefinition : fModuleNodes.values()) {
				final String categoryID = moduleDefinition.getString("category");
				final String fileName = createCategoryFileName(categoryID).replace("category_", "modules_");

				IMemento memento;
				if (tocDefinitions.containsKey(fileName))
					memento = tocDefinitions.get(fileName);

				else {
					memento = XMLMemento.createWriteRoot("toc");
					memento.putString("label", "Modules");
					memento.putString("link_to", createCategoryLink(categoryID));

					tocDefinitions.put(fileName, memento);
				}

				final IMemento topicNode = memento.createChild("topic");
				topicNode.putString("href", "help/" + createHTMLFileName(moduleDefinition.getString("id")));
				topicNode.putString("label", moduleDefinition.getString("name"));
			}
		}

		for (final Entry<String, IMemento> entry : tocDefinitions.entrySet()) {
			final File targetFile = getChild(getChild(fRootFolder, "help"), entry.getKey());
			writeFile(targetFile, entry.getValue().toString());
		}

		return tocDefinitions.keySet();
	}

	public static String createHTMLFileName(final String moduleID) {
		return "module_" + escape(moduleID) + ".html";
	}

	/**
	 * Create HTML help pages for module classes.
	 *
	 * @param classes
	 * @return <code>true</code> when at least 1 HTML file was created
	 * @throws Exception
	 *             on file creation errors
	 */
	private boolean createHTMLFiles(final DocTrees docTree,
                                    final Set<? extends Element> classes) throws IOException {
		boolean createdFiles = false;
		boolean documentationErrors = false;
		boolean invalidFileContent = false;

		for (final Element clazz : classes) {
            // only add classes which are registered in our modules lookup table
			if (fModuleNodes.containsKey(clazz.toString())) {
				// class found to create help for
				final HTMLWriter htmlWriter = new HTMLWriter(clazz, fLinkProvider, fModuleNodes.get(clazz.toString()).getChildren("dependency"), docTree);
				final String content = htmlWriter.createContents(fModuleNodes.get(clazz.toString()).getString("name"));

				if (!htmlWriter.getDocumentationErrors().isEmpty()) {
					documentationErrors = true;

					// print errors
					System.out.println((fFailOnMissingDocs ? "ERROR" : "WARNING") + ": missing documentation content for " + clazz + ":");
					for (final String errorMessage : htmlWriter.getDocumentationErrors())
						System.out.println("\t" + errorMessage);

					System.out.println("");
				}

				try {
					verifyContent(content);
				} catch (final Exception e) {
					System.out.println((fFailOnHTMLErrors ? "ERROR" : "WARNING") + ": invalid file content for " + clazz + ":");
					System.out.println("\t" + e.getMessage());
					System.out.println("");

					invalidFileContent = true;
				}

				// write document
				final File targetFile = getChild(getChild(fRootFolder, "help"), createHTMLFileName(fModuleNodes.get(clazz.toString()).getString("id")));
				writeFile(targetFile, content);
				createdFiles = true;
			}
		}

		if ((fFailOnMissingDocs) && (documentationErrors))
			throw new IOException("Documentation is not complete");

		if ((fFailOnHTMLErrors) && (invalidFileContent))
			throw new IOException("Documentation invalid");

		return createdFiles;
	}

	/**
	 * Verifies that the HTML content is well formed and correct. This guarantees that the code can be displayed in help hovers and code completion proposals.
	 *
	 * @throws Exception
	 *             when content is not well formed
	 */
	private void verifyContent(String content) throws Exception {
		// try to read content into an XMLMemento
		XMLMemento.createReadRoot(new StringReader(content));
	}

	private static void writeFile(final File file, final String data) throws IOException {
		if (!file.getParentFile().exists())
			file.getParentFile().mkdirs();

		// save data to file
		if (!file.exists())
			file.createNewFile();

		final FileWriter writer = new FileWriter(file);
		writer.write(data);
		writer.close();
	}

	private static String escape(final String data) {
		return data.replace(' ', '_').toLowerCase();
	}

	private void createModuleLookupTable() {
		fModuleNodes = new HashMap<>();

		// read plugin.xml
		File pluginXML = getChild(fRootFolder, "plugin.xml");
		if (!pluginXML.exists())
			pluginXML = getChild(fRootFolder, "fragment.xml");

		try {
			final IMemento root = XMLMemento.createReadRoot(new InputStreamReader(new FileInputStream(pluginXML)));
			for (final IMemento extensionNode : root.getChildren("extension")) {
				if ("org.eclipse.ease.modules".equals(extensionNode.getString("point"))) {
					for (final IMemento instanceNode : extensionNode.getChildren("module"))
						fModuleNodes.put(instanceNode.getString("class"), instanceNode);

					for (final IMemento instanceNode : extensionNode.getChildren("category"))
						fCategoryNodes.add(instanceNode);
				}
			}
		} catch (final Exception e) {
		}
	}

	@Override
	public void init(Locale locale, Reporter reporter) {
		this.reporter = reporter;
	}

	@Override
	public String getName() {
		return "EclipseEaseHelpGeneratorDoclet";
	}

	@Override
	public Set<? extends Option> getSupportedOptions() {
		return Set.of(
				new org.eclipse.ease.helpgenerator.Option(OPTION_PROJECT_ROOT,
						optionLength(OPTION_PROJECT_ROOT) - 1, "Root of the project", "<string>") {
					@Override
					public boolean process(String s, List<String> list) {
						fRootFolder = new File(list.get(0));
						return true;
					}
				},
				new org.eclipse.ease.helpgenerator.Option(OPTION_FAIL_ON_HTML_ERRORS.toString(),
						optionLength(OPTION_FAIL_ON_HTML_ERRORS.toString()) - 1, "Fail on HTML Error", "<boolean>") {
					@Override
					public boolean process(String s, List<String> list) {
						fFailOnHTMLErrors = Boolean.parseBoolean(list.get(0));
						return true;
					}
				},
				new org.eclipse.ease.helpgenerator.Option(OPTION_FAIL_ON_MISSING_DOCS.toString(),
						optionLength(OPTION_FAIL_ON_MISSING_DOCS.toString()) - 1, "Fail on Missing Docs", "<boolean>") {
					@Override
					public boolean process(String s, List<String> list) {
						fFailOnMissingDocs = Boolean.parseBoolean(list.get(0));
						return true;
					}
				},
				new org.eclipse.ease.helpgenerator.Option(OPTION_LINK.toString(),
						optionLength(OPTION_LINK.toString()) - 1, "Official documentation link to be included in the generated files", "<string>") {
					@Override
					public boolean process(String s, List<String> list) {
						try {
							fLinkProvider.registerAddress(list.get(0), parsePackages(new URL(list.get(0) + "/package-list").openStream()));
						} catch (final MalformedURLException e) {
							System.out.println("Error: cannot parse external URL " + list.get(0));
						} catch (final IOException e) {
							System.out.println("Error: cannot read from " + list.get(0));

						}
						return true;
					}
				},
				new org.eclipse.ease.helpgenerator.Option(OPTION_LINK_OFFLINE.toString(),
						optionLength(OPTION_LINK_OFFLINE.toString()) - 1, "Subpackages", "<string>") {
					@Override
					public boolean process(String s, List<String> list) {

						try {
							final URL url = new URL(list.get(1) + "/package-list");
							fLinkProvider.registerAddress(list.get(0), parsePackages(url.openStream()));

						} catch (final MalformedURLException e) {
							// invalid URI

							try {
								// try to read from local file
								fLinkProvider.registerAddress(list.get(0), parsePackages(new FileInputStream(list.get(1) + File.separator + "package-list")));
							} catch (final FileNotFoundException e1) {
								System.out.println("Error: cannot read from " + list.get(1));
							}
						} catch (final IOException e) {
							System.out.println("Error: cannot read from " + list.get(1));
						}
						return true;
					}
				}
		);
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.RELEASE_11;
	}

	@Override
	public boolean run(DocletEnvironment docletEnvironment) {
		return process(docletEnvironment);
	}
}
