        # ColorPicker: remove the "@COMMAND@" command created by postinst
        if [ -L @COMMAND_LINK@ ] && [ "$(readlink @COMMAND_LINK@)" = "@LAUNCHER@" ]; then
            rm -f @COMMAND_LINK@
        fi
