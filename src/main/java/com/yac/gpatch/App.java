package com.yac.gpatch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.yac.gpatch.util.FileUtils;
import com.yac.gpatch.util.logging.Log;

import picocli.CommandLine;
import picocli.CommandLine.MissingParameterException;

import com.yac.gpatch.matcher.PatchMatcher;
import com.yac.gpatch.model.Patch;
import com.yac.gpatch.parser.PatchParser;

public class App {

    public static void main(String[] args) throws Exception {
        AppConfig appConfig = populateAppConfig(args);

        Patch patch = PatchParser.parse(appConfig.getPatchPath());
        List<Log> parserLogs = PatchParser.validate(patch);
        if (parserLogs.size() > 0) {
            parserLogs.forEach(Log::print);
            exitOnError();
        } else {
            List<Path> paths = new ArrayList<>();
            try {
                for (Path file : appConfig.getPaths()) {
                    paths.addAll(FileUtils.getAllPathsByExtension(file, appConfig.getExtension()));
                }
            } catch (IOException e) {
                exitOnError();
            }

            try {
                runMatchers(patch, paths);
            } catch (Exception e) {
                exit();
            }
        }
    }

    private static void exit() {
        System.exit(0);
    }

    private static void exitOnError() {
        System.exit(-1);
    }

    private static AppConfig populateAppConfig(String[] args) {
        String[] args2 = { "--p-file", "/home/alan/Documents/dev/_test/gpatch/patch",
                "/home/alan/Documents/dev/_test/gpatch/", "-e", ".ts" };
        args = args2;

        AppConfig appConfig = new AppConfig();
        CommandLine commandLine = new CommandLine(appConfig);

        try {
            commandLine.parse(args);
        } catch (MissingParameterException e) {
            System.err.println("Error: Missing required argument(s)");
            exitOnError();
        }

        if (commandLine.isUsageHelpRequested()) {
            commandLine.usage(System.out);
            exit();
        } else if (commandLine.isVersionHelpRequested()) {
            commandLine.printVersionHelp(System.out);
            exit();
        }

        return appConfig;
    }

    private static void runMatchers(Patch patch, List<Path> paths) throws ExecutionException, InterruptedException {
        int matcherNb = Math.min(Runtime.getRuntime().availableProcessors(), paths.size());

        List<List<Path>> batchList = new ArrayList<>();
        for (int i = 0; i < matcherNb; i++) {
            batchList.add(new ArrayList<>());
        }
        for (int i = 0; i < paths.size(); i++) {
            batchList.get(i).add(paths.get(i % matcherNb));
        }

        List<PatchMatcher> matchers = IntStream.range(0, matcherNb)
                .mapToObj(i -> new PatchMatcher(patch, batchList.get(i))).collect(Collectors.toList());

        ExecutorService executor = Executors.newFixedThreadPool(matcherNb);
        CompletionService<List<Log>> completionService = new ExecutorCompletionService<>(executor);
        matchers.forEach(completionService::submit);
        executor.shutdown();
        for (int i = matchers.size(); i > 0; i--) {
            List<Log> matcherLogs = completionService.take().get();
            if (matcherLogs != null) {
                matcherLogs.forEach(Log::print);
            }
        }

    }

}
