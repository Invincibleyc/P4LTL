//#Safe
/*
 * Global Variables
 */
var stoppingFlag, stoppingEvent, stopped : bool;
var pendingIo : int;

/*
 *
 */
procedure ULTIMATE.start()
modifies stoppingFlag, stoppingEvent, stopped, pendingIo;
{
  pendingIo := 1;
  stopped := false;
  stoppingEvent := false;
  stoppingFlag := false;

  fork 00 ServerThread();

  fork 01 DeviceThreadWithAssert();
  fork 02 DeviceThread();
  fork 03 DeviceThread();
  fork 04 DeviceThread();
  fork 05 DeviceThread();
  fork 06 DeviceThread();
  fork 07 DeviceThread();
  fork 08 DeviceThread();
  fork 09 DeviceThread();
}

/*
 * Device thread ("Add")
 */
procedure DeviceThreadWithAssert()
modifies pendingIo, stoppingEvent;
{
  while (*) {
    call Enter();

    // do work
    assert pendingIo >= 1;

    call Exit();
  }
}

procedure DeviceThread()
modifies pendingIo, stoppingEvent;
{
  while (*) {
    call Enter();

    // do work

    call Exit();
  }
}

procedure Enter()
modifies pendingIo;
{
  atomic {
    assume !stoppingFlag;
    pendingIo := pendingIo + 1;
  }
}

procedure Exit()
modifies pendingIo, stoppingEvent;
{
  atomic {
    pendingIo := pendingIo - 1;
    if (pendingIo == 0) {
      //stoppingEvent := true;
    }
  }
}

/*
 * Server thread ("Stop")
 */
procedure ServerThread()
modifies stoppingFlag, pendingIo, stoppingEvent, stopped;
{
  stoppingFlag := true;
  call Close();
  assume stoppingEvent;
  stopped := true;
}

procedure Close()
modifies pendingIo, stoppingEvent;
{
  atomic {
    pendingIo := pendingIo - 1;
    if (pendingIo == 0) {
      stoppingEvent := true;
    }
  }
}
