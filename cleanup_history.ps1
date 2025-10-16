# PowerShell Script to remove copilot-swe-agent[bot] from repository history
# This script rewrites the Git history to replace all commits by copilot-swe-agent[bot]
# with commits authored by the repository owner (kab102395)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Repository History Cleanup Script" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script will:"
Write-Host "  1. Rewrite all commits to change author from copilot-swe-agent[bot] to kab102395"
Write-Host "  2. Clean up the repository"
Write-Host "  3. Force push the changes to remote"
Write-Host ""
Write-Host "WARNING: This will rewrite Git history!" -ForegroundColor Yellow
Write-Host "Anyone who has cloned this repository will need to re-clone or reset their local copy." -ForegroundColor Yellow
Write-Host ""

$confirm = Read-Host "Do you want to continue? (yes/no)"

if ($confirm -ne "yes") {
    Write-Host "Operation cancelled." -ForegroundColor Red
    exit 0
}

Write-Host ""
Write-Host "Step 1: Configuring Git user..." -ForegroundColor Green
git config user.name "kab102395"
git config user.email "kab102395@users.noreply.github.com"

Write-Host ""
Write-Host "Step 2: Rewriting commit history..." -ForegroundColor Green
Write-Host "(This may take a few moments...)" -ForegroundColor Gray

# Set environment variable to squelch filter-branch warning
$env:FILTER_BRANCH_SQUELCH_WARNING = "1"

$filterScript = @'
$OLD_EMAIL="198982749+Copilot@users.noreply.github.com"
$CORRECT_NAME="kab102395"
$CORRECT_EMAIL="kab102395@users.noreply.github.com"

if ($env:GIT_COMMITTER_EMAIL -eq $OLD_EMAIL) {
    $env:GIT_COMMITTER_NAME = $CORRECT_NAME
    $env:GIT_COMMITTER_EMAIL = $CORRECT_EMAIL
}
if ($env:GIT_AUTHOR_EMAIL -eq $OLD_EMAIL) {
    $env:GIT_AUTHOR_NAME = $CORRECT_NAME
    $env:GIT_AUTHOR_EMAIL = $CORRECT_EMAIL
}
'@

# For PowerShell, we need to use the bash version of filter-branch
# Git for Windows includes bash, so we'll use that
$bashFilterScript = @'
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
'@

git filter-branch --force --env-filter $bashFilterScript --tag-name-filter cat -- --branches --tags

Write-Host ""
Write-Host "Step 3: Cleaning up repository..." -ForegroundColor Green
if (Test-Path ".git/refs/original") {
    Remove-Item -Recurse -Force ".git/refs/original"
}
git reflog expire --expire=now --all
git gc --prune=now --aggressive

Write-Host ""
Write-Host "Step 4: Verifying changes..." -ForegroundColor Green
Write-Host "New contributor list:" -ForegroundColor Cyan
git shortlog -sne --all

Write-Host ""
Write-Host "Step 5: Force pushing to remote..." -ForegroundColor Green
$confirmPush = Read-Host "Ready to force push? This cannot be undone! (yes/no)"

if ($confirmPush -ne "yes") {
    Write-Host "Force push cancelled. Changes are local only." -ForegroundColor Yellow
    Write-Host "Run 'git push --force origin <branch-name>' when ready." -ForegroundColor Yellow
    exit 0
}

# Get current branch name
$branch = git rev-parse --abbrev-ref HEAD
Write-Host "Pushing to branch: $branch" -ForegroundColor Cyan

git push --force origin $branch

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host "✓ History cleanup complete!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Verification:" -ForegroundColor Cyan
git shortlog -sne --all
Write-Host ""
Write-Host "Note: Other contributors should re-clone the repository or run:" -ForegroundColor Yellow
Write-Host "  git fetch origin" -ForegroundColor Gray
Write-Host "  git reset --hard origin/$branch" -ForegroundColor Gray
