#!/bin/bash

PINATA_API_KEY="${PINATA_API_KEY}"
PINATA_SECRET_API_KEY="${PINATA_SECRET_API_KEY}"
GATEWAY_URL="https://gateway.pinata.cloud/ipfs"

function pinata_push {
    REPO=$1
    if [ -z "$REPO" ]; then
        echo "Usage: git-pinata push <repo>"
        exit 1
    fi

    BUNDLE="/tmp/${REPO}.bundle"
    echo "Creating Git bundle..."
    git -C "$REPO" bundle create "$BUNDLE" --all

    echo "Uploading to Pinata..."
    RESPONSE=$(curl -s -X POST "https://api.pinata.cloud/pinning/pinFileToIPFS" \
        -H "pinata_api_key: $PINATA_API_KEY" \
        -H "pinata_secret_api_key: $PINATA_SECRET_API_KEY" \
        -F "file=@${BUNDLE}")

    CID=$(echo "$RESPONSE" | grep -o '"IpfsHash":"[^"]*' | cut -d'"' -f4)

    if [ -z "$CID" ]; then
        echo "Error: Upload failed"
        echo "Response: $RESPONSE"
        exit 1
    fi

    echo "$CID" > "${REPO}/.git-pinata"
    echo "Success! Repo pushed to IPFS"
    echo "CID: $CID"
    echo "Gateway: $GATEWAY_URL/$CID"
    rm "$BUNDLE"
}

function pinata_clone {
    CID=$1
    DEST=$2

    if [ -z "$CID" ] || [ -z "$DEST" ]; then
        echo "Usage: git-pinata clone <CID> <directory>"
        exit 1
    fi

    echo "Fetching bundle from IPFS..."
    wget -q "$GATEWAY_URL/$CID" -O repo.bundle

    echo "Cloning from bundle..."
    git clone repo.bundle "$DEST"

    echo "$CID" > "${DEST}/.git-pinata"
    rm repo.bundle
    echo "Clone complete!"
}

function pinata_pull {
    REPO=$1
    if [ -z "$REPO" ]; then
        echo "Usage: git-pinata pull <repo>"
        exit 1
    fi

    if [ ! -f "${REPO}/.git-pinata" ]; then
        echo "Error: No .git-pinata file found in $REPO"
        exit 1
    fi

    CID=$(cat "${REPO}/.git-pinata")
    echo "Pulling latest bundle from CID: $CID"

    wget -q "$GATEWAY_URL/$CID" -O /tmp/repo.bundle

    echo "Fetching into repo..."
    git -C "$REPO" fetch /tmp/repo.bundle '*:*'

    rm /tmp/repo.bundle
    echo "Pull complete!"
}

case "$1" in
    push)
        shift
        pinata_push "$@"
        ;;
    clone)
        shift
        pinata_clone "$@"
        ;;
    pull)
        shift
        pinata_pull "$@"
        ;;
    *)
        echo "Usage: git-pinata {push|clone|pull}"
        exit 1
        ;;
esac
