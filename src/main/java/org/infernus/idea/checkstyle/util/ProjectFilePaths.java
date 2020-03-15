package org.infernus.idea.checkstyle.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.function.Function;

public class ProjectFilePaths {

    private static final Logger LOG = Logger.getInstance(ProjectFilePaths.class);

    private static final String IDEA_PROJECT_DIR = "$PROJECT_DIR$";

    private final Project project;
    private final char separatorChar;
    private final Function<File, String> absolutePathOf;

    public ProjectFilePaths(@NotNull final Project project) {
        this(project, File.separatorChar, File::getAbsolutePath);
    }

    public ProjectFilePaths(@NotNull final Project project,
                            final char separatorChar,
                            @NotNull final Function<File, String> absolutePathOf) {
        this.project = project;
        this.separatorChar = separatorChar;
        this.absolutePathOf = absolutePathOf;
    }

    @Nullable
    public String makeProjectRelative(@Nullable final String path) {
        if (path == null || project.isDefault()) {
            return path;
        }

        final File projectPath = projectPath(project);
        if (projectPath == null) {
            LOG.debug("Couldn't find project path, returning full path: " + path);
            return path;
        }

        try {
            final String basePath = absolutePathOf.apply(projectPath) + separatorChar;
            return basePath + FilePaths.relativePath(path, basePath, "" + separatorChar);

        } catch (FilePaths.PathResolutionException e) {
            LOG.debug("No common path was found between " + path + " and " + projectPath.getAbsolutePath());
            return path;

        } catch (Exception e) {
            LOG.warn("Failed to make relative: " + path, e);
            return path;
        }
    }

    @Nullable
    public String tokenise(@Nullable final String fsPath) {
        if (fsPath == null) {
            return null;
        }

        if (project.isDefault()) {
            if (new File(fsPath).exists() || fsPath.startsWith(IDEA_PROJECT_DIR)) {
                return toUnixPath(fsPath);
            } else {
                return IDEA_PROJECT_DIR + toUnixPath(separatorChar + fsPath);
            }
        }

        final File projectPath = projectPath(project);
        if (projectPath != null && fsPath.startsWith(absolutePathOf.apply(projectPath) + separatorChar)) {
            return IDEA_PROJECT_DIR
                    + toUnixPath(fsPath.substring(absolutePathOf.apply(projectPath).length()));
        }

        return toUnixPath(fsPath);
    }

    @Nullable
    public String detokenise(@Nullable final String tokenisedPath) {
        if (tokenisedPath == null) {
            return null;
        }

        String detokenisedPath = replaceProjectToken(tokenisedPath, project);

        if (detokenisedPath == null) {
            detokenisedPath = toSystemPath(tokenisedPath);
        }
        return detokenisedPath;
    }

    private String replaceProjectToken(final String path, final Project project) {
        int prefixLocation = path.indexOf(IDEA_PROJECT_DIR);
        if (prefixLocation >= 0) {
            final File projectPath = projectPath(project);
            if (projectPath != null) {
                final String projectRelativePath = toSystemPath(path.substring(prefixLocation + IDEA_PROJECT_DIR.length()));
                final String completePath = projectPath + File.separator + projectRelativePath;
                return absolutePathOf.apply(new File(completePath));

            } else {
                LOG.warn("Could not detokenise path as project dir is unset: " + path);
            }
        }
        return null;
    }

    private String toUnixPath(final String systemPath) {
        if (separatorChar == '/') {
            return systemPath;
        }
        return systemPath.replace(separatorChar, '/');
    }

    private String toSystemPath(final String unixPath) {
        if (separatorChar == '/') {
            return unixPath;
        }
        return unixPath.replace('/', separatorChar);
    }

    @Nullable
    private File projectPath(final Project project) {
        try {
            final VirtualFile baseDir = project.getBaseDir();
            if (baseDir == null) {
                return null;
            }

            return new File(baseDir.getPath());

        } catch (Exception e) {
            // IDEA 10.5.2 sometimes throws an AssertionException in project.getBaseDir()
            LOG.debug("Couldn't retrieve base location", e);
            return null;
        }
    }
}
