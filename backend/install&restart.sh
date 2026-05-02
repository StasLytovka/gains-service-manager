sudo cp build/libs/gains-service-manager.jar \
        /opt/gains-service-manager/backend/
sudo systemctl restart gains-manager
#sudo journalctl -u gains-manager -f
sudo systemctl restart gains-manager
