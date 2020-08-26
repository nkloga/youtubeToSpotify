# youtubeToSpotify

Add tracks from a youtube playlist to a spotify playlist.

Process:
- Application is getting a list of tracks from a youtube playlist
- searching for a track id in the spotify
- adding up id's to a string (50 ids in one string) and adding them to the spotify playlist

You will need id's and API keys in order to use the app, add them in the following format to the config.properties file:
  YOUTUBE_API_KEY = ""
  SPOTIFY_API_KEY = ""
  YOUTUBE_PLAYLIST_ID = ""
  SPOTIFY_PLAYLIST_ID = ""
