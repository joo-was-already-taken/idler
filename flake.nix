{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
  };

  outputs = inputs@{ self, nixpkgs, flake-parts, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [ "x86_64-linux" "aarch64-linux" "aarch64-darwin" "x86_64-darwin" ];

      perSystem = { system, pkgs, ... }: let
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ "34.0.0" ];
          platformVersions = [ "34" ];

          includeEmulator = false;
          includeSystemImages = false;
          includeSources = false;
        };

        androidSdk = androidComposition.androidsdk;
      in {
        _module.args.pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jdk17
            gradle
            jdt-language-server
            androidSdk
            fdroidserver
          ];
          ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
          GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/34.0.0/aapt2";
          shellHook = /*bash*/ ''
            ln -sfn "${androidSdk}/libexec/android-sdk" .android-sdk
            if [[ ! -f .classpath ]]; then
              ./gradlew eclipse --no-configuration-cache -q
            fi
          '';
        };
      };
    };
}
