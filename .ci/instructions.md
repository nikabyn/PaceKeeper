# Instructions to build Dockerfile

To build the Docker Image you'll need to execute the following command from the .ci folder:
```bash
docker build -f Dockerfile --build-arg API_LEVEL=<API_LEVEL> -t gitlab.dit.htwk-leipzig.de:5050/pacing-app/ui/android-emulator:api<API_LEVEL> --platform linux/amd64 --output type=registry --provenance=false .
```

For example to build the images for API28 or 36 you can use the following calls:
```bash
#28
docker build -f Dockerfile --build-arg API_LEVEL=28 gitlab.dit.htwk-leipzig.de:5050/pacing-app/ui/android-emulator:api28 --platform linux/amd64 --output type=registry --provenance=false .

#36
docker build -f Dockerfile --build-arg API_LEVEL=36 gitlab.dit.htwk-leipzig.de:5050/pacing-app/ui/android-emulator:api36 --platform linux/amd64 --output type=registry --provenance=false .
```