# git-pinata

Backup and clone Git repositories over **IPFS** using [Pinata Cloud](https://www.pinata.cloud/).
This tool wraps Git’s bundle mechanism with Pinata’s IPFS gateway so you can `push`, `clone`, and `pull` repos without needing a traditional Git server.

---

## ✨ Features

* **git-pinata push** → Bundle repo and upload to IPFS via Pinata.
* **git-pinata clone** → Fetch bundle by CID and restore the repo.
* **git-pinata pull** → Fetch updates from the last pushed bundle into an existing repo.
* Works just like Git, but over IPFS!
* Zero servers, decentralized storage.

---

## 📦 Installation

1. Clone this repo and copy the script to your PATH:

   ```bash
   chmod +x git-pinata.sh
   cp git-pinata.sh ~/.local/bin/git-pinata
   ```

   (make sure `~/.local/bin` is in your `$PATH`)

2. Set your Pinata API credentials in `.env` or in your shell:

   ```bash
   export PINATA_API_KEY=your_api_key
   export PINATA_SECRET_API_KEY=your_secret_api_key
   ```

---

## 🚀 Usage

### Push a repo

```bash
git-pinata push <repo>
```

Example:

```bash
git-pinata push DC-IPFS
```

Output:

```
Success! Repo pushed to IPFS
CID: QmdeBWvGgioLPCe6kEsBGaUF9pB3U2vhqTKSwvXf5XaA9E
Gateway: https://gateway.pinata.cloud/ipfs/QmdeBWvGgioLPCe6kEsBGaUF9pB3U2vhqTKSwvXf5XaA9E
```

### Clone a repo from IPFS

```bash
git-pinata clone <CID> <directory>
```

Example:

```bash
git-pinata clone QmdeBWvGgioLPCe6kEsBGaUF9pB3U2vhqTKSwvXf5XaA9E DC-IPFS
```

### Pull updates into existing repo

```bash
git-pinata pull <repo>
```

Example:

```bash
git-pinata pull DC-IPFS
```

---

## 🛠 How it works

* `git bundle` creates a self-contained archive of the repo.
* The bundle is uploaded to IPFS using Pinata’s **pinFileToIPFS** endpoint.
* The resulting **CID** is stored in `.git-pinata` inside the repo.
* Clone and pull operations use this CID to fetch bundles back from the IPFS gateway.

---

## ⚠️ Notes

* `pull` works like `git fetch`, so you may need to merge or checkout branches.
* Large repos may take time to upload/download depending on Pinata/IPFS speed.
* Requires valid Pinata API keys (JWT is optional).

---

## 📜 License

MIT License.
