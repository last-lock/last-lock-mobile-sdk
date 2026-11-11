require 'fileutils'
require 'xcodeproj'

# Copies the React Native bridge into the app target and links the published
# Last Lock Mobile iOS package. That package downloads the binaries and compiles
# LastLockMobileSdkTypes from the sources it ships.
SDK_REPOSITORY = 'https://github.com/last-lock/link-mobile-sdk-prod.git'
SDK_VERSION = '2.7.6'
SDK_PRODUCT = 'LastLockMobileSdk'

def integrate_lastlock_mobile(project_name)
  ios_dir = Pod::Config.instance.installation_root
  source_dir = File.expand_path(File.dirname(__FILE__))
  project_path = File.join(ios_dir, "#{project_name}.xcodeproj")
  app_dir = File.join(ios_dir, project_name)

  FileUtils.mkdir_p(app_dir)
  %w[LastLockMobileSdkModule.swift LastLockMobileSdkModule.mm LastLockMobileSdkController.h].each do |name|
    FileUtils.cp(File.join(source_dir, name), File.join(app_dir, name))
  end
  obsolete_names = %w[LinkMobileModule.swift LinkMobileModule.mm LinkMobileModule.m LinkMobileController.h LastLockMobileSdkModule.m]
  obsolete_names.each { |name| FileUtils.rm_f(File.join(app_dir, name)) }

  project = Xcodeproj::Project.open(project_path)
  target = project.targets.find { |item| item.name == project_name }
  raise "#{project_name} target not found" unless target

  group = project.main_group.children.find { |child| child.display_name == project_name }
  raise "#{project_name} group not found" unless group

  project.files.select { |file| obsolete_names.include?(File.basename(file.path.to_s)) && file.path.start_with?("#{project_name}/") }.each do |legacy|
    target.source_build_phase.files.each do |build_file|
      build_file.remove_from_project if build_file.file_ref == legacy
    end
    legacy.remove_from_project
  end

  %w[LastLockMobileSdkModule.swift LastLockMobileSdkModule.mm LastLockMobileSdkController.h].each do |name|
    path = "#{project_name}/#{name}"
    file_ref = project.files.find { |file| file.path == path }
    file_ref ||= group.new_file(path)
    next if name.end_with?('.h')

    already_compiled = target.source_build_phase.files.any? { |file| file.file_ref == file_ref }
    target.add_file_references([file_ref]) unless already_compiled
  end

  target.build_configurations.each do |config|
    config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '17.0'
  end

  package_ref = project.root_object.package_references.find do |ref|
    ref.isa == 'XCRemoteSwiftPackageReference' && ref.repositoryURL == SDK_REPOSITORY
  end
  unless package_ref
    package_ref = project.new(Xcodeproj::Project::Object::XCRemoteSwiftPackageReference)
    package_ref.repositoryURL = SDK_REPOSITORY
    project.root_object.package_references << package_ref
  end
  package_ref.requirement = { 'kind' => 'exactVersion', 'version' => SDK_VERSION }

  product = target.package_product_dependencies.find { |item| [SDK_PRODUCT, 'LinkMobile'].include?(item.product_name) }
  unless product
    product = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
    target.package_product_dependencies << product
  end
  product.package = package_ref
  product.product_name = SDK_PRODUCT

  # Updating the SDK must not leave an unused dev/prod package in the project.
  used_packages = project.objects.grep(Xcodeproj::Project::Object::XCSwiftPackageProductDependency).map(&:package)
  project.root_object.package_references.to_a.each do |ref|
    next unless ref.respond_to?(:repositoryURL) && ref.repositoryURL.to_s.include?('/last-lock/link-mobile-sdk-')
    next if used_packages.include?(ref)
    project.root_object.package_references.delete(ref)
    ref.remove_from_project
  end

  already_linked = target.frameworks_build_phase.files.any? { |file| file.product_ref == product }
  unless already_linked
    build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
    build_file.product_ref = product
    target.frameworks_build_phase.files << build_file
  end

  project.save
end
