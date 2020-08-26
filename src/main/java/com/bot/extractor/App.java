package com.bot.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.utils.URIBuilder;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

public class App {

    private static String YOUTUBE_API_KEY;
    private static String SPOTIFY_API_KEY;
    private static String YOUTUBE_PLAYLIST_ID;
    private static String SPOTIFY_PLAYLIST_ID;

    public static void main(String[] args) throws IOException, URISyntaxException {

        InputStream input = new FileInputStream("src/main/resources/config.properties");
        Properties prop = new Properties();
        prop.load(input);
        YOUTUBE_API_KEY = prop.getProperty("YOUTUBE_API_KEY");
        SPOTIFY_API_KEY = prop.getProperty("SPOTIFY_API_KEY");
        YOUTUBE_PLAYLIST_ID = prop.getProperty("YOUTUBE_PLAYLIST_ID");
        SPOTIFY_PLAYLIST_ID = prop.getProperty("SPOTIFY_PLAYLIST_ID");
        input.close();
        List<String> youtubeTracks = getTracksFromYoutubePlaylist(YOUTUBE_API_KEY, YOUTUBE_PLAYLIST_ID);
        List<String> spotifyTrackIds = getListOfSpotifyIds(youtubeTracks);
        List<String> spotifyTrackIdsFlatten = addListItemsToString(spotifyTrackIds, 50);

        for (String track : spotifyTrackIdsFlatten) {
            addToSpotifyPlaylist(track);
        }
    }

    private static ArrayList<String> getTracksFromYoutubePlaylist(String key, String playlistId) throws URISyntaxException, IOException {
        String pageToken = "";
        ArrayList<String> list = new ArrayList<>();

        while (true) {
            URI uri = new URIBuilder("https://www.googleapis.com/youtube/v3/playlistItems?")
                    .addParameter("part", "snippet")
                    .addParameter("key", key)
                    .addParameter("playlistId", playlistId)
                    .addParameter("maxResults", "50")
                    .addParameter("pageToken", pageToken)
                    .build();

            HttpURLConnection con = createHttpRequest(uri, "GET", null);
            if (con.getResponseCode() != 200) {
                System.out.println("HTTP request error, code: " + con.getResponseCode());
                con.disconnect();
                break;
            }
            String content = appendHttpResponse(con);
            con.disconnect();

            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(content, Map.class);
            try {
                ArrayList<LinkedHashMap> linkedHashMaps = (ArrayList<LinkedHashMap>) map.get("items");
                list.addAll(linkedHashMaps
                        .stream()
                        .map(i -> i.get("snippet"))
                        .map(i -> (LinkedHashMap) i)
                        .map(i -> i.get("title").toString())
                        .collect(Collectors.toList()));
                Object nextToken = map.get("nextPageToken");
                if (nextToken != null) {
                    pageToken = nextToken.toString();
                } else {
                    break;
                }
            } catch (ClassCastException e) {
                System.out.println(e);
            }

        }
        return list;
    }

    static private String appendHttpResponse(HttpURLConnection con) throws IOException {
        InputStream is = con.getInputStream();
        String content = readResponse(is);
        is.close();
        return content;
    }

    static private List<String> getListOfSpotifyIds(List<String> youtubeTracks) throws IOException, URISyntaxException {
        List<String> spotifyTracks = new ArrayList<>();
        for (String s : youtubeTracks) {
            String ids = searchSpotifyTrack(s);
            if (ids != null) {
                spotifyTracks.add(ids);
            }
        }
        return spotifyTracks;
    }

    static private String searchSpotifyTrack(String trackName) throws URISyntaxException, IOException {
        URI uri = new URIBuilder("https://api.spotify.com/v1/search?")
                .addParameter("q", trackName)
                .addParameter("type", "track")
                .build();
        String listId = null;
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("Authorization", "Bearer " + SPOTIFY_API_KEY);
        HttpURLConnection con = createHttpRequest(uri, "GET", parameters);
        if (con.getResponseCode() == 200 || con.getResponseCode() == 201) {
            String content = appendHttpResponse(con);
            listId = parseSpotifyResponse(content);
        }
        return listId;
    }

    static private HttpURLConnection createHttpRequest(URI uri, String method, HashMap<String, String> parameters) throws IOException {
        HttpURLConnection con = null;
        int counter = 10; //try to reconnect maximum 10 times
        while (counter != 0) {
            con = (HttpURLConnection) uri.toURL().openConnection();
            con.setRequestMethod(method);
            con.setRequestProperty("Content-Type", "application/json");
            if (parameters != null) {
                for (Map.Entry<String, String> p : parameters.entrySet()) {
                    con.setRequestProperty(p.getKey(), p.getValue());
                }
            }
            if (method.equals("POST")) {
                con.setDoOutput(true);
                String body = parameters != null ? parameters.get("Body") : null;
                try (OutputStream os = con.getOutputStream()) {
                    byte[] input = body != null ? body.getBytes("utf-8") : new byte[0];
                    os.write(input, 0, input.length);
                }
            }
            if (con.getResponseCode() == 429) {
                System.out.println("429, counter: " + counter);
                counter--;
                try {
                    int waitTime = Integer.parseInt(con.getHeaderField("retry-after"));
                    System.out.println("Wait time " + waitTime);
                    Thread.sleep(waitTime + 800);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                con.disconnect();
            } else if (con.getResponseCode() != 200 && con.getResponseCode() != 201) {
                System.out.println("HTTP code: " + con.getResponseCode());
                con.disconnect();
            } else {
                counter = 0;
            }
        }
        return con;
    }


    // spotify playlist endpoint can receive up to 100 track ids at the time, ids should be separated with comma
    static private List<String> addListItemsToString(List<String> items, int listLength) {
        ArrayList<String> spotifyTracks = new ArrayList<>();
        while (items != null) {
            if (items.size() > listLength) {
                spotifyTracks.add(String.join(",", items.subList(0, listLength)));
                items = items.subList(listLength, items.size());
            } else {
                spotifyTracks.add(String.join(",", items.subList(0, items.size())));
                items = null;
            }
        }
        return spotifyTracks;
    }

    static private void addToSpotifyPlaylist(String trackName) throws URISyntaxException, IOException {
        String addToPlaylistUri = "https://api.spotify.com/v1/playlists/" + SPOTIFY_PLAYLIST_ID + "/tracks?";
        URI uri = new URIBuilder(addToPlaylistUri)
                .addParameter("uris", trackName)
                .build();
        HashMap<String, String> properties = new HashMap<>();
        properties.put("Authorization", "Bearer " + SPOTIFY_API_KEY);
        properties.put("Content-Length", "0");
        properties.put("Body", "{}");
        HttpURLConnection con = createHttpRequest(uri, "POST", properties);
        if (con.getResponseCode() == 201 || con.getResponseCode() == 200) {
            String content = appendHttpResponse(con);
            System.out.println(content);
        }
        con.disconnect();
    }

    static private String readResponse(InputStream is) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(is));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        return content.toString();
    }


    private static String parseSpotifyResponse(String content) throws IOException {
        String response = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(content, Map.class);
            LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> linkedHashMaps = (LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>>) map.get("tracks");
            ArrayList<LinkedHashMap<String, String>> id = linkedHashMaps.get("items");
            if (id != null && id.size() > 0) {
                response = id.get(0).get("uri");
            }
        } catch (NullPointerException e) {
            System.out.println(e.getStackTrace());
        }
        return response;
    }
}

