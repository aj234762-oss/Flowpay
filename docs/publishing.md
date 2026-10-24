# Publishing FlowPay

This guide covers publishing the app images to Docker Hub and the source code to GitHub. It is separate from the project README so the README can stay focused on installing and using FlowPay.

## Publish images to Docker Hub

The existing public Docker Hub repository is `arjunsingh63/flowpay`. This project uses a separate tag for each service so the older image tags, including `latest`, are left unchanged.

From the project folder, sign in and check Buildx:

```sh
docker login --username arjunsingh63
docker buildx version
docker buildx inspect --bootstrap
```

Build and push the backend, frontend, and webhook receiver for both common CPU architectures:

```sh
docker buildx build --platform linux/amd64,linux/arm64 \
  -t arjunsingh63/flowpay:razor-v2-backend --push .

docker buildx build --platform linux/amd64,linux/arm64 \
  -f frontend/Dockerfile \
  -t arjunsingh63/flowpay:razor-v2-frontend --push ./frontend

docker buildx build --platform linux/amd64,linux/arm64 \
  -f receiver/Dockerfile \
  -t arjunsingh63/flowpay:razor-v2-receiver --push ./receiver
```

The `compose.yaml` file references these tags. After all three pushes succeed, download and start the published images with:

```sh
docker compose pull
docker compose up -d --no-build
```

To rebuild from source instead, use `docker compose up --build -d`.

## Update the existing GitHub source repository

The previous source repository is <https://github.com/aj234762-oss/Flowpay>. Clone it first so the new version keeps the repository's existing history. Do not initialize a separate Git repository in the project folder and force-push it over the old one.

Run these commands from the parent folder that contains `Razor`:

```sh
git clone https://github.com/aj234762-oss/Flowpay.git Flowpay-github
rsync -av \
  --exclude='.git/' \
  --exclude='.idea/' \
  --exclude='.DS_Store' \
  --exclude='.env' \
  --exclude='.env.*' \
  --exclude='node_modules/' \
  --exclude='dist/' \
  --exclude='target/' \
  Razor/ Flowpay-github/
cd Flowpay-github
git status
git add -A
git commit -m "Update FlowPay simulator"
git push
```

Review `git status` before committing. The `.gitignore` excludes local environment files, dependencies, and build outputs; the `rsync` command also skips them when copying. Never commit passwords, API keys, webhook secrets, or `.env` files. If GitHub asks for credentials, authenticate using GitHub's supported sign-in or credential manager; do not put a password or token into the repository URL.
