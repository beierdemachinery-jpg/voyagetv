#!/usr/bin/env bash
# 在任意 macOS 机器（或 GitHub Actions 运行器）上生成工程并打包未签名 IPA。
#
# 用法：
#   ./Scripts/build-ipa.sh            # 编译 + 测试 + 出 IPA
#   ./Scripts/build-ipa.sh build      # 只编译（最快，用于看编译错误）
#
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-all}"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "缺少 xcodegen，请先执行: brew install xcodegen"
  exit 1
fi

echo "==> 生成工程"
xcodegen generate

echo "==> 模拟器编译"
xcodebuild build \
  -project VoyageTV.xcodeproj \
  -scheme VoyageTV \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/DerivedData \
  CODE_SIGNING_ALLOWED=NO

if [ "$MODE" = "build" ]; then
  echo "==> 完成（仅编译）"
  exit 0
fi

echo "==> 单元测试"
xcodebuild test \
  -project VoyageTV.xcodeproj \
  -scheme VoyageTV \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -derivedDataPath build/DerivedData \
  CODE_SIGNING_ALLOWED=NO

echo "==> 归档（未签名）"
rm -rf build/VoyageTV.xcarchive build/Payload
xcodebuild archive \
  -project VoyageTV.xcodeproj \
  -scheme VoyageTV \
  -configuration Release \
  -sdk iphoneos \
  -archivePath build/VoyageTV.xcarchive \
  -derivedDataPath build/DerivedData \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGN_ENTITLEMENTS=""

echo "==> 打包 IPA"
mkdir -p build/Payload
cp -R build/VoyageTV.xcarchive/Products/Applications/VoyageTV.app build/Payload/
(cd build && zip -qry VoyageTV-unsigned.ipa Payload)

echo "==> 产物: $(pwd)/build/VoyageTV-unsigned.ipa"
echo "    下一步：用 Sideloadly（Windows 可用）以你的 Apple ID 签名后装到 iPhone。"
