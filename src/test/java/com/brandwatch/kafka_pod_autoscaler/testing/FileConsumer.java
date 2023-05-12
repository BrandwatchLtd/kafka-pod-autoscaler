package com.brandwatch.kafka_pod_autoscaler.testing;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testcontainers.containers.output.BaseConsumer;
import org.testcontainers.containers.output.OutputFrame;

import lombok.SneakyThrows;

public class FileConsumer extends BaseConsumer<FileConsumer> {
    private final OutputStream fileStream;

    @SneakyThrows
    public FileConsumer(Path file) {
        this.fileStream = Files.newOutputStream(file);
    }

    @SneakyThrows
    @Override
    public void accept(OutputFrame outputFrame) {
        if (outputFrame.getType() == OutputFrame.OutputType.END) {
            fileStream.close();
            return;
        }
        fileStream.write(outputFrame.getBytes());
    }
}
