#!/usr/bin/env python3
#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Writes the power HAL configs of the performance profiles (Settings > System >
# Performance), one folder per SoC: <soc>/powerhint-<profile>.json.
#
#   python3 configs/power/gen_profiles.py
#
# Edit the tables here and run it again rather than editing the JSON by hand. The page
# in ExynosParts (PerfProfiles.java) shows the same numbers, so keep the two in step.
#
# Every profile writes its CPU and GPU caps once when the HAL starts (ResetOnInit, after
# boot completes): the kernel boots at Samsung's clocks and only the overclock profiles
# raise them. "Touch" is what INTERACTION holds while the screen is touched; "launch" is
# the app launch boost (full speed for that many ms). 9999999 means "the cap".

import json
import os

MAX = 9999999

SOCS = {
    # A30, A30s, A40
    'exynos7904': {
        'stock': (1586000, 1768000, 845000),
        'oc1': (1690000, 1976000, 1001000),
        'oc2': (1690000, 2080000, 1100000),
        'touch': {
            'light': (1014000, 1144000, 450000),
            'smooth': (1144000, 1352000, 676000),
            'fast': (1352000, 1560000, 845000),
            'max': (MAX, MAX, 845000),
            'oc1': (1586000, 1768000, 845000),
            'oc2': (1586000, 1768000, 845000),
        },
    },
    # A10, A20, A20e
    'exynos7884B': {
        'stock': (1352000, 1560000, 676000),
        'oc1': (1586000, 1768000, 845000),
        'oc2': (1690000, 2080000, 1001000),
        'touch': {
            'light': (1014000, 1144000, 450000),
            'smooth': (1144000, 1352000, 676000),
            'fast': (1248000, 1560000, 676000),
            'max': (MAX, MAX, 676000),
            'oc1': (1352000, 1560000, 676000),
            'oc2': (1352000, 1560000, 676000),
        },
    },
}

# profile: (app launch boost in ms, scheduler boost to the big cores, GPU clock held
# for launches, games and heavy blur; None = the GPU cap). Smooth is the old default
# powerhint.json, unchanged.
BOOST = {
    'light': (2000, False, 545000),
    'smooth': (5000, True, 676000),
    'fast': (5000, True, None),
    'max': (8000, True, None),
    'oc1': (8000, True, None),
    'oc2': (8000, True, None),
}

PROFILES = ['stock', 'light', 'smooth', 'fast', 'max', 'oc1', 'oc2']

CPU0 = '/sys/devices/system/cpu/cpu0/cpufreq/'
CPU6 = '/sys/devices/system/cpu/cpu6/cpufreq/'


def node(name, path, values, default, reset=True):
    # libperfmgr gives the first value in the list the highest priority.
    values = sorted({str(v) for v in values} | {str(default)}, key=int, reverse=True)
    return {'Name': name, 'Path': path, 'Values': values,
            'DefaultIndex': values.index(str(default)), 'ResetOnInit': reset}


def action(hint, node_name, value, duration=0):
    return {'PowerHint': hint, 'Node': node_name, 'Duration': duration, 'Value': str(value)}


def profile(soc, name):
    s = SOCS[soc]
    cap = s.get(name, s['stock'])
    nodes = [
        {'Name': 'TouchscreenEnable', 'Path': '/sys/class/sec/tsp/input/enabled',
         'Values': ['1', '0'], 'ResetOnInit': True},
        {'Name': 'KernelBatterySaver', 'Path': '/sys/module/battery_saver/parameters/enabled',
         'Values': ['1', '0'], 'ResetOnInit': True},
        {'Name': 'DoubleTapToWakeEnable', 'Path': '/sys/class/sec/tsp/cmd',
         'Values': ['aot_enable,0', 'aot_enable,1'], 'DefaultIndex': 0, 'ResetOnInit': False},
        # The caps: written once at start and never touched by a hint.
        node('CPULittleClusterMaxFreq', CPU0 + 'scaling_max_freq', [], cap[0]),
        node('CPUBigClusterMaxFreq', CPU6 + 'scaling_max_freq', [], cap[1]),
        node('GPUMaxClock', '/sys/kernel/gpu/gpu_max_clock', [], cap[2]),
    ]
    actions = [
        action('DOUBLE_TAP_TO_WAKE', 'DoubleTapToWakeEnable', 'aot_enable,1'),
        action('INTERACTIVE', 'TouchscreenEnable', 1),
        action('LOW_POWER', 'KernelBatterySaver', 1),
    ]
    if name != 'stock':
        touch = s['touch'][name]
        launch_ms, hmp, gpu_top = BOOST[name]
        gpu_top = gpu_top or cap[2]
        nodes += [
            node('CPULittleClusterMinFreq', CPU0 + 'scaling_min_freq', [MAX, touch[0]], 208000),
            node('CPUBigClusterMinFreq', CPU6 + 'scaling_min_freq', [MAX, touch[1]], 208000),
            node('GPUMinClock', '/sys/kernel/gpu/gpu_min_clock', [gpu_top, touch[2]], 0),
        ]
        if hmp:
            nodes.append(node('HMPBoost', '/sys/kernel/hmp/boost', [1], 0))
        sets = [
            ('INTERACTION', 0, touch[0], touch[1], touch[2]),
            ('LAUNCH', launch_ms, MAX, MAX, gpu_top),
            ('FIXED_PERFORMANCE', 0, MAX, MAX, gpu_top),
        ]
        for hint, ms, little, big, gpu in sets:
            actions += [
                action(hint, 'CPULittleClusterMinFreq', little, ms),
                action(hint, 'CPUBigClusterMinFreq', big, ms),
                action(hint, 'GPUMinClock', gpu, ms),
            ]
            if hmp:
                actions.append(action(hint, 'HMPBoost', 1, ms))
        actions.append(action('EXPENSIVE_RENDERING', 'GPUMinClock', gpu_top))
    return {'Nodes': nodes, 'Actions': actions}


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    for soc in SOCS:
        os.makedirs(os.path.join(here, soc), exist_ok=True)
        for name in PROFILES:
            path = os.path.join(here, soc, 'powerhint-%s.json' % name)
            with open(path, 'w') as f:
                json.dump(profile(soc, name), f, indent=4)
                f.write('\n')
            print(path)


if __name__ == '__main__':
    main()
