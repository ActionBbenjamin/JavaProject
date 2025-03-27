import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class Main {
    private static final boolean TESTING_MODE = true;
    private static final String APITOKEN = "mRvQLRrqAuE30DBn4H0RWfBwECEXVn+qgcHVsy4dC4d5m0SW719KUb6KfCXfepWykw5tYwhxEUU=";
    private static final String IMAGE_URL = "https://www.indiewire.com/wp-content/uploads/2018/01/daniel-craig.jpg?w=300";

    public static void main(String[] args) {
        try {
            String imageFilePath = downloadImage();
            JSONArray urlsImages;

            if (TESTING_MODE) {
                System.out.println("****** TESTING MODE search, results are bad, and queue wait is long, but credits are NOT deducted ******");
            }

            String site = "https://facecheck.id";
            String authToken = APITOKEN;

            // Upload the image
            JSONObject uploadResponse = uploadImage(site, authToken, imageFilePath);
            if (uploadResponse.has("error") && !uploadResponse.isNull("error")) {
                System.out.println("Error Code: " + uploadResponse.getString("code") + " , " + uploadResponse.getString("error"));
                return;
            }

            String idSearch = uploadResponse.getString("id_search");
            System.out.println(uploadResponse.getString("message") + " id_search=" + idSearch);

            JSONObject jsonData = new JSONObject()
                    .put("id_search", idSearch)
                    .put("with_progress", true)
                    .put("status_only", false)
                    .put("demo", TESTING_MODE);

            while (true) {
                JSONObject searchResponse = searchImage(site, authToken, jsonData);
                if (searchResponse.has("error") && !searchResponse.isNull("error")) {
                    System.out.println("Error Code: " + searchResponse.getString("code") + " , " + searchResponse.getString("error"));
                    return;
                }

                if (searchResponse.has("output") && !searchResponse.isNull("output")) {
                    urlsImages = searchResponse.getJSONObject("output").getJSONArray("items");
                    break;
                }

                System.out.println(searchResponse.getString("message") + " progress: " + searchResponse.getInt("progress") + "%");
                Thread.sleep(1000);
            }

            if (urlsImages != null) {
                for (int i = 0; i < urlsImages.length(); i++) {
                    JSONObject im = urlsImages.getJSONObject(i);
                    int score = im.getInt("score");
                    String url = im.getString("url");
                    String imageBase64 = im.getString("base64");

                    System.out.println(score + " " + url + " " + imageBase64.substring(0, 32) + "...");
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String getImageExtensionFromURL(String imageUrl) throws MalformedURLException {
        try {
            URL url = new URL(imageUrl);
            String path = url.getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            return fileName.substring(fileName.lastIndexOf('.') + 1);
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
            throw exception;
        }
    }

    private static String downloadImage() throws IOException {
        String imageFileName = "SearchImage." + getImageExtensionFromURL(IMAGE_URL);
        URL imageUrl = new URL(IMAGE_URL);
        try (InputStream in = imageUrl.openStream()) {
            Files.copy(in, Paths.get(imageFileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            System.out.println(exception.getMessage());
            throw exception;
        }
        return imageFileName;
    }

    private static JSONObject uploadImage(String site, String authToken, String imageFilePath) throws IOException {
        String boundary = "*****" + Long.toHexString(System.currentTimeMillis()) + "*****";
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        URL url = new URL(site + "/api/upload_pic");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("accept", "application/json");
        connection.setRequestProperty("Authorization", authToken);
        connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

        try (DataOutputStream dos = new DataOutputStream(connection.getOutputStream())) {
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"images\";filename=\"" + imageFilePath + "\"" + lineEnd);
            dos.writeBytes(lineEnd);

            try (FileInputStream fis = new FileInputStream(imageFilePath)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
            }

            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            dos.flush();
        }

        try (InputStream is = connection.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return new JSONObject(response.toString());
        }
    }

    private static JSONObject searchImage(String site, String authToken, JSONObject jsonData) throws IOException {
        URL url = new URL(site + "/api/search");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("accept", "application/json");
        connection.setRequestProperty("Authorization", authToken);
        connection.setRequestProperty("Content-Type", "application/json");

        try (DataOutputStream dos = new DataOutputStream(connection.getOutputStream())) {
            dos.writeBytes(jsonData.toString());
            dos.flush();
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
            throw exception;
        }

        try (InputStream is = connection.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return new JSONObject(response.toString());
        } catch (NoRouteToHostException exception) {
            System.out.println(exception.getMessage());
            throw exception;
        }
    }
}
