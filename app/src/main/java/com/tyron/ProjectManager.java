package com.tyron;

import com.tyron.code.completion.provider.CompletionEngine;
import com.tyron.code.model.Project;
import com.tyron.code.parser.FileManager;
import com.tyron.code.ui.editor.log.LogViewModel;
import com.tyron.resolver.DependencyDownloader;
import com.tyron.resolver.DependencyResolver;
import com.tyron.resolver.DependencyUtils;
import com.tyron.resolver.model.Dependency;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

public class ProjectManager {

    public interface TaskListener {
        void onTaskStarted(String message);
        void onComplete(boolean success, String message);
    }

    private final LogViewModel mLogger;

    public ProjectManager(LogViewModel logger) {
        mLogger = logger;
    }

    public void openProject(Project proj, boolean downloadLibs, TaskListener mListener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (downloadLibs) {
                mListener.onTaskStarted("Resolving dependencies");

                // this is the existing libraries from app/libs
                Set<Dependency> libs = DependencyUtils.fromLibs(proj.getLibraryDirectory());

                // dependencies parsed from the build.gradle file
                Set<Dependency> dependencies = new HashSet<>();
                try {
                    dependencies.addAll(DependencyUtils.parseGradle(new File(proj.mRoot, "app/build.gradle")));
                } catch (Exception exception) {
                    //TODO: handle parse error
                    mListener.onComplete(false, exception.getMessage());
                }

                DependencyResolver resolver = new DependencyResolver(dependencies, proj.getLibraryDirectory());
                resolver.addResolvedLibraries(libs);
                dependencies = resolver.resolveMain();
               // mLogger.d(LogViewModel.BUILD_LOG, "Resolved dependencies: " + dependencies);

                mListener.onTaskStarted("Downloading dependencies");
             //   mLogger.d(LogViewModel.BUILD_LOG, "Downloading dependencies");
                DependencyDownloader downloader = new DependencyDownloader(libs, proj.getLibraryDirectory());
                try {
                    downloader.download(dependencies);
                } catch (IOException e) {

                }
            }

            mListener.onTaskStarted("Indexing");

            FileManager.getInstance().openProject(proj);
            CompletionEngine.getInstance().index(proj, () -> mListener.onComplete(true, "Index successful"));
        });
    }
}
