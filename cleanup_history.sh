#!/bin/bash

# Script to remove copilot-swe-agent[bot] from repository history
# This script rewrites the Git history to replace all commits by copilot-swe-agent[bot]
# with commits authored by the repository owner (kab102395)

set -e  # Exit on error

echo "=========================================="
echo "Repository History Cleanup Script"
echo "=========================================="
echo ""
echo "This script will:"
echo "  1. Rewrite all commits to change author from copilot-swe-agent[bot] to kab102395"
echo "  2. Clean up the repository"
echo "  3. Force push the changes to remote"
echo ""
echo "WARNING: This will rewrite Git history!"
echo "Anyone who has cloned this repository will need to re-clone or reset their local copy."
echo ""
read -p "Do you want to continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Operation cancelled."
    exit 0
fi

echo ""
echo "Step 1: Configuring Git user..."
git config user.name "kab102395"
git config user.email "kab102395@users.noreply.github.com"

echo ""
echo "Step 2: Rewriting commit history..."
git filter-branch --force --env-filter '
OLD_EMAIL="198982749+Copilot@users.noreply.github.com"
CORRECT_NAME="kab102395"
CORRECT_EMAIL="kab102395@users.noreply.github.com"

if [ "$GIT_COMMITTER_EMAIL" = "$OLD_EMAIL" ]
then
    export GIT_COMMITTER_NAME="$CORRECT_NAME"
    export GIT_COMMITTER_EMAIL="$CORRECT_EMAIL"
fi
if [ "$GIT_AUTHOR_EMAIL" = "$OLD_EMAIL" ]
then
    export GIT_AUTHOR_NAME="$CORRECT_NAME"
    export GIT_AUTHOR_EMAIL="$CORRECT_EMAIL"
fi
' --tag-name-filter cat -- --branches --tags

echo ""
echo "Step 3: Cleaning up repository..."
rm -rf .git/refs/original/
git reflog expire --expire=now --all
git gc --prune=now --aggressive

echo ""
echo "Step 4: Verifying changes..."
echo "New contributor list:"
git shortlog -sne --all

echo ""
echo "Step 5: Force pushing to remote..."
read -p "Ready to force push? This cannot be undone! (yes/no): " confirm_push

if [ "$confirm_push" != "yes" ]; then
    echo "Force push cancelled. Changes are local only."
    echo "Run 'git push --force origin <branch-name>' when ready."
    exit 0
fi

# Get current branch name
BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "Pushing to branch: $BRANCH"

git push --force origin "$BRANCH"

echo ""
echo "=========================================="
echo "✓ History cleanup complete!"
echo "=========================================="
echo ""
echo "Verification:"
git shortlog -sne --all
echo ""
echo "Note: Other contributors should re-clone the repository or run:"
echo "  git fetch origin"
echo "  git reset --hard origin/$BRANCH"
