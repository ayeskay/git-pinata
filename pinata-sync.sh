#!/bin/bash
# Manual sync utility

# Load environment variables
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

case "$1" in
    "backup")
        ./git-pinata.sh
        ;;
    "restore")
        if [ -f "last-backup.cid" ]; then
            CID=$(cat last-backup.cid)
            echo "Restoring from CID: $CID"
            curl -s "https://gateway.pinata.cloud/ipfs/$CID" -o "/tmp/repo-restore.bundle"
            echo "Bundle downloaded. To restore:"
            echo "  git clone /tmp/repo-restore.bundle restored-repo"
        else
            echo "No backup found. Run backup first."
        fi
        ;;
    "status")
        if [ -f "last-backup.cid" ]; then
            echo "Last backup:"
            cat last-backup.cid
            echo "URL: $(cat last-backup.url)"
        else
            echo "No backups found"
        fi
        ;;
    *)
        echo "Usage: $0 {backup|restore|status}"
        ;;
esac
