# -*- mode: ruby -*-
# vi: set ft=ruby :

PIO_PROVISION = "scalaio-vagrant.sh"
PIO_PROVISION_ARGS = "'vagrant'"

# Vagrantfile API/syntax version. Don't touch unless you know what you're doing!
VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  # All Vagrant configuration is done here. The most common configuration
  # options are documented and commented below. For a complete reference,
  # please see the online documentation at vagrantup.com.

  # Every Vagrant virtual environment requires a box to build off of.
  config.vm.box = "precise64"

  # The URL that the configured box can be found at.
  # If the box is not installed on the system, it will be retrieved from this URL when vagrant up is run.
  config.vm.box_url = "http://files.vagrantup.com/precise64.box"

  # see http://www.virtualbox.org/manual/ch08.html#vboxmanage-modifyvm
  config.vm.provider "virtualbox" do |v|
    v.customize ["modifyvm", :id, "--cpuexecutioncap", "90", "--memory", "2048"]
  end

  # The url from where the 'config.vm.box' box will be fetched if it
  # doesn't already exist on the user's system.
  # config.vm.box_url = "http://domain.com/path/to/above.box"

  # install PredictionIO
  config.vm.provision :shell do |s|
    s.path = PIO_PROVISION
    s.args = PIO_PROVISION_ARGS
  end
  config.vm.synced_folder ".", "/home/vagrant/cfp"
  config.vm.network :forwarded_port, guest: 9000, host: 4090
  config.vm.network :forwarded_port, guest: 3000, host: 3090
end
