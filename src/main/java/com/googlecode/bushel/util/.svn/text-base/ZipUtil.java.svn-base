package com.googlecode.bushel.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Class derived from code posted here: http://forums.sun.com/thread.jspa?messageID=2160923
 */
public class ZipUtil {
    public static void zip(File sourceDir, OutputStream targetStream) throws IOException {
        if (!sourceDir.isFile() && !sourceDir.isDirectory()) {
            return;
        }

        final ZipOutputStream cpZipOutputStream = new ZipOutputStream(targetStream);
        cpZipOutputStream.setLevel(9);
        zipFiles(sourceDir, sourceDir, cpZipOutputStream);
        cpZipOutputStream.finish();
        cpZipOutputStream.close();
    }

    private static void zipFiles(File rootDir, File currDir, ZipOutputStream zos) throws IOException {
        if (currDir.isDirectory()) {
            final File[] files = currDir.listFiles();
            for (int i = 0; i < files.length; i++) {
                zipFiles(rootDir, files[i], zos);
            }
        } else {
            final String strAbsPath = currDir.getPath();
            final String strZipEntryName = strAbsPath.substring(rootDir.getPath().length() + 1, strAbsPath.length());

            final byte[] b = new byte[(int) (currDir.length())];
            final FileInputStream fis = new FileInputStream(currDir);
            fis.read(b);
            fis.close();
            
            final ZipEntry entry = new ZipEntry(strZipEntryName);
            zos.putNextEntry(entry);
            zos.write(b, 0, (int) currDir.length());
            zos.closeEntry();
        }
    }
}
