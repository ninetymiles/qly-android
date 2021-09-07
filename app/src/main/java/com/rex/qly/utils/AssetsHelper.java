package com.rex.qly.utils;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class AssetsHelper {

    private final Context mContext;

    public AssetsHelper(Context context) {
        mContext = context;
    }

    public void clearPath(File dstFilePath) {
        if (dstFilePath.isDirectory()) {
            File[] files = dstFilePath.listFiles();
            if (files != null) {
                for (File f : files) {
                    clearPath(f);
                }
            }
        }
        dstFilePath.delete();
    }

    public File exportAssetFile(String srcFileName, String dstFilePath) throws IOException {
        File dstFile = new File(dstFilePath + File.separator + srcFileName);
        InputStream is = mContext.getAssets().open(srcFileName);
        OutputStream os = new FileOutputStream(dstFile);
        byte[] buf = new byte[1024];
        int count;
        while ((count = is.read(buf)) != -1) {
            os.write(buf, 0, count);
        }
        os.close();
        is.close();
        return dstFile;
    }

    public void unzip(File zipFile, File dstPath) throws ZipException, IOException {
        if (!dstPath.exists()) {
            dstPath.mkdirs();
        }
        ZipFile zf = new ZipFile(zipFile);
        for (Enumeration<?> entries = zf.entries(); entries.hasMoreElements(); ) {
            ZipEntry entry = ((ZipEntry) entries.nextElement());
            if (entry.isDirectory()) {
                new File(dstPath + File.separator + entry.getName()).mkdir();
                continue;
            }
            File dstFile = new File(dstPath + File.separator + entry.getName());
            InputStream in = zf.getInputStream(entry);
            OutputStream out = new FileOutputStream(dstFile);
            byte buffer[] = new byte[1024];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
            in.close();
            out.close();
        }
        zf.close();
    }
}
