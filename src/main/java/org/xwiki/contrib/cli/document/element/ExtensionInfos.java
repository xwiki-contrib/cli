package org.xwiki.contrib.cli.document.element;

/**
 * Represent an extension ID. Mostly used for the dependency management for script services dependencies.
 *
 * @param groupId group id of the extension.
 * @param artefactId artefact ID of the extension.
 * @param version version of the extension.
 *
 * @version $Id$
 */
public record ExtensionInfos(
    String groupId,
    String artefactId,
    String version)
{
}

