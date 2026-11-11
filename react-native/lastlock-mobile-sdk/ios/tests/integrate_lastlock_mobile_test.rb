require 'minitest/autorun'
require 'tmpdir'
require 'cocoapods'
require_relative '../integrate_lastlock_mobile'

class IntegrateLastLockMobileTest < Minitest::Test
  # Existing apps must migrate the SDK's generated files and product exactly once.
  def test_migrates_legacy_sdk_and_is_idempotent
    Dir.mktmpdir do |directory|
      Pod::Config.instance.installation_root = Pathname.new(directory)
      app_dir = File.join(directory, 'TimeKeeper')
      FileUtils.mkdir_p(app_dir)
      project_path = File.join(directory, 'TimeKeeper.xcodeproj')
      project = Xcodeproj::Project.new(project_path)
      target = project.new_target(:application, 'TimeKeeper', :ios, '17.0')
      group = project.main_group.new_group('TimeKeeper')
      legacy_names = %w[LinkMobileModule.swift LinkMobileModule.mm LinkMobileController.h]
      legacy_names.each do |name|
        File.write(File.join(app_dir, name), '// Old SDK-generated adapter')
        ref = group.new_file("TimeKeeper/#{name}")
        target.add_file_references([ref]) unless name.end_with?('.h')
      end
      package = project.new(Xcodeproj::Project::Object::XCRemoteSwiftPackageReference)
      package.repositoryURL = 'https://github.com/last-lock/link-mobile-sdk-prod.git'
      package.requirement = { 'kind' => 'exactVersion', 'version' => '0.0.1' }
      project.root_object.package_references << package
      product = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
      product.product_name = 'LinkMobile'
      product.package = package
      target.package_product_dependencies << product
      build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
      build_file.product_ref = product
      target.frameworks_build_phase.files << build_file
      project.save

      2.times { integrate_lastlock_mobile('TimeKeeper') }

      updated = Xcodeproj::Project.open(project_path)
      target = updated.targets.find { |item| item.name == 'TimeKeeper' }
      assert_equal ['LastLockMobileSdk'], target.package_product_dependencies.map(&:product_name)
      assert_equal 1, target.frameworks_build_phase.files.count { |item| item.product_ref }
      assert_equal ['LastLockMobileSdkModule.mm', 'LastLockMobileSdkModule.swift'],
                   target.source_build_phase.files.map { |item| File.basename(item.file_ref.path) }.sort
      assert_equal [SDK_REPOSITORY], updated.root_object.package_references.map(&:repositoryURL)
      legacy_names.each do |name|
        refute File.exist?(File.join(app_dir, name)), name
        refute updated.files.any? { |item| File.basename(item.path.to_s) == name }, name
      end
    end
  end
end
