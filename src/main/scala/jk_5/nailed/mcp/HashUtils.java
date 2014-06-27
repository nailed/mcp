package jk_5.nailed.mcp;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.io.ByteStreams;

/**
 * No description given
 *
 * @author jk-5
 */
public class HashUtils {

    public static final String HASH_FUNC = "MD5";

    public static String hash(File file) {
        if(file.getPath().endsWith(".zip") || file.getPath().endsWith(".jar")){
            return hashZip(file, HASH_FUNC);
        }else{
            return hash(file, HASH_FUNC);
        }
    }

    public static List<String> hashAll(File file) {
        LinkedList<String> list = new LinkedList<String>();

        if(file.isDirectory()){
            for(File f : file.listFiles()){
                hashAll(f);
            }
        }else{
            list.add(hash(file));
        }

        return list;
    }

    public static String hash(File file, String function) {

        try{
            InputStream fis = new FileInputStream(file);
            byte[] array = ByteStreams.toByteArray(fis);
            fis.close();

            return hash(array, function);
        }catch(Exception e){
            e.printStackTrace();
        }

        return null;
    }

    public static String hashZip(File file, String function) {
        try{
            MessageDigest hasher = MessageDigest.getInstance(function);

            ZipInputStream zin = new ZipInputStream(new FileInputStream(file));
            ZipEntry entry = null;
            while((entry = zin.getNextEntry()) != null){
                hasher.update(entry.getName().getBytes());
                hasher.update(ByteStreams.toByteArray(zin));
            }
            zin.close();

            byte[] hash = hasher.digest();


            // convert to string
            String result = "";

            for(int i = 0; i < hash.length; i++){
                result += Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1);
            }
            return result;
        }catch(Exception e){
            e.printStackTrace();
        }

        return null;
    }

    public static String hash(String str) {
        return hash(str.getBytes());
    }

    public static String hash(byte[] bytes) {
        return hash(bytes, HASH_FUNC);
    }

    public static String hash(byte[] bytes, String function) {
        try{
            MessageDigest complete = MessageDigest.getInstance(function);
            byte[] hash = complete.digest(bytes);

            String result = "";

            for(int i = 0; i < hash.length; i++){
                result += Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1);
            }
            return result;
        }catch(Exception e){
            e.printStackTrace();
        }

        return null;
    }
}
