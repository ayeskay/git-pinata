#!/bin/bash
# Git to Pinata backup script

# Load environment variables
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# Configuration
PINATA_API="https://api.pinata.cloud/pinning/pinFileToIPFS"
REPO_PATH=${1:-"$(pwd)"}
TIMESTAMP=$(date +%s)

# Check if we're in a Git repo
if [ ! -d "${REPO_PATH}/.git" ] && [ ! -d ".git" ]; then
    echo "Error: Not in a Git repository"
    exit 1
fi

# Change to repo root if needed
if [ -d ".git" ]; then
    REPO_ROOT="."
else
    REPO_ROOT="${REPO_PATH}"
fi

cd "${REPO_ROOT}"

# Get repository name (folder name)
REPO_NAME=$(basename "$(pwd)")
BUNDLE_NAME="${REPO_NAME}-${TIMESTAMP}.bundle"

echo "Creating Git bundle for repository: $REPO_NAME"
echo "Bundle name: $BUNDLE_NAME"

# Create temporary bundle
git bundle create "/tmp/${BUNDLE_NAME}" --all

if [ $? -ne 0 ]; then
    echo "Error: Failed to create Git bundle"
    exit 1
fi

# Upload to Pinata
echo "Uploading to Pinata..."
RESPONSE=$(curl -s -X POST "$PINATA_API" \
    -H "pinata_api_key: $PINATA_API_KEY" \
    -H "pinata_secret_api_key: $PINATA_SECRET_API_KEY" \
    -F file=@"/tmp/${BUNDLE_NAME}" \
    -F pinataMetadata="{\"name\":\"${REPO_NAME}\",\"keyvalues\":{\"repo\":\"${REPO_NAME}\",\"timestamp\":${TIMESTAMP}}}")

# Extract CID from response
CID=$(echo "$RESPONSE" | grep -o '"IpfsHash":"[^"]*"' | cut -d'"' -f4)

if [ -n "$CID" ]; then
    echo "Success! Repository backed up to IPFS"
    echo "Repository name: $REPO_NAME"
    echo "CID: $CID"
    echo "Gateway URL: https://gateway.pinata.cloud/ipfs/$CID"
    echo "Clone command: git-pinata clone $CID $REPO_NAME"
    
    # Save info for easy access
    echo "$CID" > "last-backup.cid"
    echo "$REPO_NAME" > "last-backup-name.txt"
    echo "https://gateway.pinata.cloud/ipfs/$CID" > "last-backup.url"
    echo "git-pinata clone $CID $REPO_NAME" > "last-clone-command.txt"
else
    echo "Error: Upload failed"
    echo "Response: $RESPONSE"
    rm "/tmp/${BUNDLE_NAME}"
    exit 1
fi

# Cleanup
rm "/tmp/${BUNDLE_NAME}"
echo "Backup complete!"
