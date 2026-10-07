package net.emeraldsmp;
public final class EmeraldSMP extends EmeraldSMPBase {
 @Override public void onEnable(){ super.onEnable(); FinalRestorationPatch.install(this); }
}