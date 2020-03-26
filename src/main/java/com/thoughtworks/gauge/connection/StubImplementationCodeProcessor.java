// Copyright 2019 ThoughtWorks, Inc.

// This file is part of Gauge-Java.

// This program is free software.
//
// It is dual-licensed under:
// 1) the GNU General Public License as published by the Free Software Foundation,
// either version 3 of the License, or (at your option) any later version;
// or
// 2) the Eclipse Public License v1.0.
//
// You can redistribute it and/or modify it under the terms of either license.
// We would then provide copied of each license in a separate .txt file with the name of the license as the title of the file.

package com.thoughtworks.gauge.connection;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.google.protobuf.ProtocolStringList;
import com.thoughtworks.gauge.FileHelper;
import com.thoughtworks.gauge.Logger;
import gauge.messages.Messages;
import gauge.messages.Spec;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class StubImplementationCodeProcessor implements com.thoughtworks.gauge.processor.IMessageProcessor {
    private static final String NEW_LINE = "\n";
    private static ArrayList<MethodDeclaration> methodDeclarations = new ArrayList<>();
    private static Range classRange;


    @Override
    public Messages.Message process(Messages.Message message) {
        ProtocolStringList stubs = message.getStubImplementationCodeRequest().getCodesList();
        String filePath = message.getStubImplementationCodeRequest().getImplementationFilePath();


        File file = new File(filePath);
        Messages.FileDiff fileDiff;

        if (file.exists()) {
            fileDiff = implementInExistingFile(stubs, file);
        } else {
            File fileName = FileHelper.getDefaultImplFileName("", 0);
            fileDiff = implementInNewClass(stubs, fileName);
        }

        return Messages.Message.newBuilder()
                .setMessageId(message.getMessageId())
                .setFileDiff(fileDiff)
                .setMessageType(Messages.Message.MessageType.StubImplementationCodeRequest)
                .build();
    }


    private Messages.FileDiff implementInExistingFile(ProtocolStringList stubs, File file) {
        try {
            if (new FileReader(file).read() != -1) {
                return implementInExistingClass(stubs, file);
            }
            return implementInNewClass(stubs, file);
        } catch (IOException e) {
            Logger.error("Unable to implement method", e);
        }
        return null;
    }

    private Messages.FileDiff implementInNewClass(ProtocolStringList stubs, File file) {
        String className = FileHelper.getClassName(file);
        String contents = getNewClassContents(className, stubs);
        Spec.Span.Builder span = Spec.Span.newBuilder()
                .setStart(0)
                .setStartChar(0)
                .setEnd(0)
                .setEndChar(0);
        Messages.TextDiff textDiff = Messages.TextDiff.newBuilder().setSpan(span).setContent(contents).build();
        return Messages.FileDiff.newBuilder().setFilePath(file.toString()).addTextDiffs(textDiff).build();
    }

    private String getNewClassContents(String className, ProtocolStringList stubs) {
        return "import com.thoughtworks.gauge.Step;"
                + NEW_LINE
                + NEW_LINE
                + "public class " + className + " {"
                + NEW_LINE
                + String.join(NEW_LINE, stubs)
                + NEW_LINE
                + "}"
                + NEW_LINE;
    }

    private Messages.FileDiff implementInExistingClass(ProtocolStringList stubs, File file) {
        try {

            JavaParser javaParser = new JavaParser();
            ParseResult<CompilationUnit> compilationUnit = javaParser.parse(file);
            String contents = String.join(NEW_LINE, stubs);
            int lastLine;
            int column;
            MethodVisitor methodVisitor = new MethodVisitor();
            methodVisitor.visit(compilationUnit.getResult().get(), null);
            if (!methodDeclarations.isEmpty()) {
                MethodDeclaration methodDeclaration = methodDeclarations.get(methodDeclarations.size() - 1);
                lastLine = methodDeclaration.getRange().get().end.line - 1;
                column = methodDeclaration.getRange().get().end.column + 1;
                contents = NEW_LINE + contents;
            } else {
                new ClassVisitor().visit(compilationUnit.getResult().get(), null);
                lastLine = classRange.end.line - 1;
                column = 0;
                contents = contents + NEW_LINE;
            }
            Spec.Span.Builder span = Spec.Span.newBuilder()
                    .setStart(lastLine)
                    .setStartChar(column)
                    .setEnd(lastLine)
                    .setEndChar(column);
            Messages.TextDiff textDiff = Messages.TextDiff.newBuilder().setSpan(span).setContent(contents).build();
            return Messages.FileDiff.newBuilder().setFilePath(file.toString()).addTextDiffs(textDiff).build();

        } catch (IOException e) {
            Logger.error("Unable to implement method", e);
        }
        return null;
    }

    private static class MethodVisitor extends VoidVisitorAdapter {
        @Override
        public void visit(MethodDeclaration methodDeclaration, Object arg) {
            methodDeclarations.add(methodDeclaration);
        }
    }

    private static class ClassVisitor extends VoidVisitorAdapter {
        @Override
        public void visit(ClassOrInterfaceDeclaration node, Object arg) {
            classRange = node.getRange().get();
        }
    }
}
